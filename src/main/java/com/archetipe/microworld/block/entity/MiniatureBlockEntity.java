package com.archetipe.microworld.block.entity;

import com.archetipe.microworld.registry.ModBlockEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MiniatureBlockEntity extends BlockEntity {

    @OnlyIn(Dist.CLIENT)
    public static final ModelProperty<BlockPos> POSITION_PROPERTY = new ModelProperty<>();

    private int scale = 16;

    private volatile Map<Integer, MiniatureVoxel> voxels = Map.of();
    private volatile Map<Integer, Float> voxelHardness = Map.of();
    private volatile VoxelShape collisionShapeCache;

    private ResourceKey<Level> sourceDimension;
    private BlockPos sourceOrigin;
    private int tickCounter;

    public MiniatureBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MINIATURE_BLOCK_ENTITY.get(), pos, state);
    }

    public int getScale() { return scale; }
    public Map<Integer, MiniatureVoxel> getVoxels() { return voxels; }

    public float getVoxelHardness(int voxelIdx) {
        Float h = voxelHardness.get(voxelIdx);
        return h != null ? h : -1f;
    }

    public ResourceKey<Level> getSourceDimension() { return sourceDimension; }
    public BlockPos getSourceOrigin() { return sourceOrigin; }

    public void setSourceLocation(ResourceKey<Level> dimension, BlockPos origin, int scale) {
        this.sourceDimension = dimension;
        this.sourceOrigin = origin;
        this.scale = scale;
        setChanged();
    }

    public void populateFromPixelData(BlockState sourceState, short[] pixelData, int scale,
                                      ServerLevel megablockLevel, BlockPos megablockOrigin) {
        this.scale = scale;
        Map<Integer, MiniatureVoxel> nextVoxels = new HashMap<>();
        Map<Integer, Float> nextHardness = new HashMap<>();

        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int i = 0; i < pixelData.length; i++) {
            short packed = pixelData[i];
            if (packed == 0) continue;
            nextVoxels.put(i, MiniatureVoxel.sampled(sourceState, packed));

            if (megablockLevel != null && megablockOrigin != null) {
                int x = i % scale;
                int rem = i / scale;
                int z = rem % scale;
                int y = rem / scale;
                mp.set(megablockOrigin.getX() + x,
                        megablockOrigin.getY() + y,
                        megablockOrigin.getZ() + z);
                BlockState mState = megablockLevel.getBlockState(mp);
                float h = mState.getDestroySpeed(megablockLevel, mp);
                nextHardness.put(i, h);
            }
        }

        this.voxels = Map.copyOf(nextVoxels);
        this.voxelHardness = Map.copyOf(nextHardness);
        invalidateShapeCache();
        setChanged();

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }

    public void updateVoxels(Map<Integer, MiniatureVoxel> next) {
        this.voxels = Map.copyOf(next);
        Map<Integer, Float> pruned = new HashMap<>();
        for (Integer key : next.keySet()) {
            Float h = voxelHardness.get(key);
            if (h != null) pruned.put(key, h);
        }
        this.voxelHardness = Map.copyOf(pruned);
        invalidateShapeCache();
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }

    public void broadcastUpdate() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        ClientboundBlockEntityDataPacket packet = getUpdatePacket();
        if (packet == null) return;
        double x = worldPosition.getX() + 0.5;
        double y = worldPosition.getY() + 0.5;
        double z = worldPosition.getZ() + 0.5;
        for (ServerPlayer player : serverLevel.players()) {
            if (player.distanceToSqr(x, y, z) < 64.0 * 64.0) {
                player.connection.send(packet);
            }
        }
    }

    // ---- Slow path (kept for later event-driven sync). ----

    public void refreshFrom(ServerLevel source, BlockPos origin, int scale) {
        int cx = origin.getX() >> 4;
        int cz = origin.getZ() >> 4;
        source.getChunk(cx, cz, ChunkStatus.FULL, true);

        Map<Integer, MiniatureVoxel> nextVoxels = new HashMap<>();
        Map<Integer, Float> nextHardness = new HashMap<>();

        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int x = 0; x < scale; x++) {
            for (int y = 0; y < scale; y++) {
                for (int z = 0; z < scale; z++) {
                    BlockPos p = origin.offset(x, y, z);
                    MiniatureVoxel v = resolveVoxel(source, p, scale);
                    if (v == null) continue;
                    int idx = (y * scale + z) * scale + x;
                    nextVoxels.put(idx, v);

                    float h = source.getBlockState(p).getDestroySpeed(source, p);
                    nextHardness.put(idx, h);
                }
            }
        }

        Map<Integer, MiniatureVoxel> immutableNextV = Map.copyOf(nextVoxels);
        Map<Integer, Float> immutableNextH = Map.copyOf(nextHardness);
        if (!immutableNextV.equals(voxels) || !immutableNextH.equals(voxelHardness)) {
            voxels = immutableNextV;
            voxelHardness = immutableNextH;
            invalidateShapeCache();
            setChanged();
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
            }
        }
    }

    private static MiniatureVoxel resolveVoxel(ServerLevel level, BlockPos p, int scale) {
        BlockState s = level.getBlockState(p);
        if (s.isAir()) return null;

        if (s.getBlock() instanceof com.archetipe.microworld.block.MicroWorldBlock) {
            BlockEntity be = level.getBlockEntity(p);
            if (!(be instanceof MicroWorldBlockEntity mw)) return null;
            MicroWorldBlockEntity master = mw.isMaster() ? mw : mw.getMasterBE();
            if (master == null) return null;

            BlockState sourceState = master.getOriginalState();
            short[] pixelData = master.getPixelData();
            if (sourceState == null || pixelData == null) return null;

            int voxelIndex;
            if (mw.isMaster()) {
                BlockPos so = master.getStructureOrigin();
                if (so == null) return null;
                int lx = p.getX() - so.getX();
                int ly = p.getY() - so.getY();
                int lz = p.getZ() - so.getZ();
                if (lx < 0 || ly < 0 || lz < 0 || lx >= scale || ly >= scale || lz >= scale) return null;
                voxelIndex = (ly * scale + lz) * scale + lx;
            } else {
                voxelIndex = mw.getIndex();
            }
            if (voxelIndex < 0 || voxelIndex >= pixelData.length) return null;
            short pixel = pixelData[voxelIndex];
            if (pixel == 0) return null;
            return MiniatureVoxel.sampled(sourceState, pixel);
        }

        return MiniatureVoxel.real(s);
    }

    public void serverTick() {
        if (++tickCounter % 20 != 0) return;
        if (sourceDimension == null || sourceOrigin == null) return;
        if (level == null || level.isClientSide) return;

        ServerLevel src = level.getServer().getLevel(sourceDimension);
        if (src == null) return;

        refreshFrom(src, sourceOrigin, scale);
    }

    // ---- Collision shape ----

    public VoxelShape getCollisionShape() {
        VoxelShape cached = collisionShapeCache;
        if (cached != null) return cached;

        Map<Integer, MiniatureVoxel> v = voxels;
        int s = scale;
        if (v.isEmpty() || s <= 0) {
            VoxelShape empty = Shapes.empty();
            collisionShapeCache = empty;
            return empty;
        }

        if (v.size() == s * s * s) {
            VoxelShape full = Shapes.block();
            collisionShapeCache = full;
            return full;
        }

        double vs = 1.0 / s;
        VoxelShape shape = Shapes.empty();
        for (Integer idxObj : v.keySet()) {
            int idx = idxObj;
            int x = idx % s;
            int rem = idx / s;
            int z = rem % s;
            int y = rem / s;
            double x0 = x * vs, y0 = y * vs, z0 = z * vs;
            VoxelShape box = Shapes.box(x0, y0, z0, x0 + vs, y0 + vs, z0 + vs);
            shape = Shapes.joinUnoptimized(shape, box, BooleanOp.OR);
        }

        VoxelShape result = shape.optimize();
        collisionShapeCache = result;
        return result;
    }

    private void invalidateShapeCache() {
        collisionShapeCache = null;
    }

    @OnlyIn(Dist.CLIENT)
    private void refreshClientRender() {
        if (level == null || !level.isClientSide) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.levelRenderer == null) return;
        requestModelDataUpdate();
        mc.levelRenderer.setSectionDirty(
                worldPosition.getX() >> 4,
                worldPosition.getY() >> 4,
                worldPosition.getZ() >> 4
        );
    }

    // ---- Compact NBT serialization ----
    // Palette + packed long per voxel. Replaces a ListTag of CompoundTags
    // (which was ~90 bytes per voxel) with a packed long (~8 bytes) plus a
    // shared palette for unique block states. This keeps the mini's update
    // tag under the chunk packet size limit.

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Scale", scale);

        if (sourceDimension != null) {
            tag.putString("SourceDim", sourceDimension.location().toString());
        }
        if (sourceOrigin != null) {
            tag.putLong("SourceOrigin", sourceOrigin.asLong());
        }

        ArrayList<BlockState> palette = new ArrayList<>();
        HashMap<BlockState, Integer> paletteLookup = new HashMap<>();
        palette.add(Blocks.AIR.defaultBlockState());
        paletteLookup.put(Blocks.AIR.defaultBlockState(), 0);

        int n = voxels.size();
        long[] packed = new long[n];
        int[] hardnessBits = new int[n];

        int i = 0;
        for (Map.Entry<Integer, MiniatureVoxel> e : voxels.entrySet()) {
            int idx = e.getKey();
            MiniatureVoxel v = e.getValue();

            Integer pObj = paletteLookup.get(v.state());
            int p;
            if (pObj == null) {
                if (palette.size() >= 256) {
                    p = 0;
                } else {
                    p = palette.size();
                    palette.add(v.state());
                    paletteLookup.put(v.state(), p);
                }
            } else {
                p = pObj;
            }

            long pack = ((long)(idx & 0xFFF))
                    | ((long)(p & 0xFF) << 12)
                    | ((long)(v.pixel() & 0xFFFF) << 20)
                    | ((long)(v.sampled() ? 1 : 0) << 36);
            packed[i] = pack;

            Float h = voxelHardness.get(idx);
            hardnessBits[i] = h != null ? Float.floatToIntBits(h) : 0;
            i++;
        }

        ListTag paletteTag = new ListTag();
        for (BlockState s : palette) {
            paletteTag.add(NbtUtils.writeBlockState(s));
        }
        tag.put("Palette", paletteTag);
        tag.putLongArray("Voxels", packed);
        tag.putIntArray("Hardness", hardnessBits);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        scale = tag.getInt("Scale");

        if (tag.contains("SourceDim")) {
            ResourceLocation rl = ResourceLocation.tryParse(tag.getString("SourceDim"));
            if (rl != null) {
                sourceDimension = ResourceKey.create(Registries.DIMENSION, rl);
            }
        }
        if (tag.contains("SourceOrigin")) {
            sourceOrigin = BlockPos.of(tag.getLong("SourceOrigin"));
        }

        // Only the compact format is supported. If an old world loads a
        // mini saved with the CompoundTag-list format, it comes back empty
        // rather than crashing.
        if (!tag.contains("Voxels", Tag.TAG_LONG_ARRAY)) {
            voxels = Map.of();
            voxelHardness = Map.of();
            invalidateShapeCache();
            refreshClientRender();
            return;
        }

        ListTag paletteTag = tag.getList("Palette", Tag.TAG_COMPOUND);
        BlockState[] palette = new BlockState[paletteTag.size()];
        for (int i = 0; i < paletteTag.size(); i++) {
            palette[i] = NbtUtils.readBlockState(
                    registries.lookupOrThrow(Registries.BLOCK),
                    paletteTag.getCompound(i));
        }

        long[] packed = tag.getLongArray("Voxels");
        int[] hardnessBits = tag.getIntArray("Hardness");

        Map<Integer, MiniatureVoxel> nextV = new HashMap<>(Math.max(packed.length, 1));
        Map<Integer, Float> nextH = new HashMap<>();

        for (int i = 0; i < packed.length; i++) {
            long p = packed[i];
            int idx = (int)(p & 0xFFF);
            int palIdx = (int)((p >> 12) & 0xFF);
            int pixel = (int)((p >> 20) & 0xFFFF);
            boolean sampled = ((p >> 36) & 1) != 0;

            BlockState state = (palIdx < palette.length)
                    ? palette[palIdx]
                    : Blocks.AIR.defaultBlockState();
            MiniatureVoxel v = sampled
                    ? MiniatureVoxel.sampled(state, (short)pixel)
                    : MiniatureVoxel.real(state);
            nextV.put(idx, v);

            if (i < hardnessBits.length) {
                float h = Float.intBitsToFloat(hardnessBits[i]);
                if (h != 0f) nextH.put(idx, h);
            }
        }

        voxels = Map.copyOf(nextV);
        voxelHardness = Map.copyOf(nextH);
        invalidateShapeCache();

        refreshClientRender();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        refreshClientRender();
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public ModelData getModelData() {
        return ModelData.builder().with(POSITION_PROPERTY, worldPosition).build();
    }
}