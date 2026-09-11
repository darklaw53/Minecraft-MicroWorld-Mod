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
import net.minecraft.world.level.block.Block;
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

import java.util.HashMap;
import java.util.Map;

public class MiniatureBlockEntity extends BlockEntity {

    @OnlyIn(Dist.CLIENT)
    public static final ModelProperty<BlockPos> POSITION_PROPERTY = new ModelProperty<>();

    private int scale = 16;

    private volatile Map<Integer, MiniatureVoxel> voxels = Map.of();

    // Voxel-shape collision derived from the current voxel map. Built lazily
    // on first query and invalidated whenever the voxel map is replaced.
    private volatile VoxelShape collisionShapeCache;

    private ResourceKey<Level> sourceDimension;
    private BlockPos sourceOrigin;

    private int tickCounter;

    public MiniatureBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MINIATURE_BLOCK_ENTITY.get(), pos, state);
    }

    public int getScale() { return scale; }
    public Map<Integer, MiniatureVoxel> getVoxels() { return voxels; }

    /**
     * Returns a VoxelShape representing all occupied voxels at their scaled
     * positions within the block. Cached; rebuilt on the next query after the
     * voxel map changes.
     *
     * The shape is derived from the raw voxel map (not VisualBaker-culled),
     * because collision is a server-side concern and VisualBaker is
     * client-only. For a fence mini this produces the fence's collision
     * shape, which is what the source block itself would give you.
     */
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

        // Fast path: fully occupied 16^3 grid -> standard full block shape.
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

    public void setSourceLocation(ResourceKey<Level> dimension, BlockPos origin, int scale) {
        this.sourceDimension = dimension;
        this.sourceOrigin = origin;
        this.scale = scale;
        setChanged();
    }

    public void populateFromPixelData(BlockState sourceState, short[] pixelData, int scale) {
        this.scale = scale;
        Map<Integer, MiniatureVoxel> next = new HashMap<>();
        for (int i = 0; i < pixelData.length; i++) {
            short packed = pixelData[i];
            if (packed == 0) continue;
            next.put(i, MiniatureVoxel.sampled(sourceState, packed));
        }
        this.voxels = Map.copyOf(next);
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

    // ---- Slow path (kept for future event-driven live sync). ----

    public void refreshFrom(ServerLevel source, BlockPos origin, int scale) {
        int cx = origin.getX() >> 4;
        int cz = origin.getZ() >> 4;
        source.getChunk(cx, cz, ChunkStatus.FULL, true);

        Map<Integer, MiniatureVoxel> next = new HashMap<>();
        for (int x = 0; x < scale; x++) {
            for (int y = 0; y < scale; y++) {
                for (int z = 0; z < scale; z++) {
                    BlockPos p = origin.offset(x, y, z);
                    MiniatureVoxel vox = resolveVoxel(source, p, scale);
                    if (vox == null) continue;
                    int idx = (y * scale + z) * scale + x;
                    next.put(idx, vox);
                }
            }
        }

        Map<Integer, MiniatureVoxel> immutableNext = Map.copyOf(next);
        if (!immutableNext.equals(voxels)) {
            voxels = immutableNext;
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

    // ---- Client render refresh ----

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

    // ---- NBT ----

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

        ListTag list = new ListTag();
        for (Map.Entry<Integer, MiniatureVoxel> e : voxels.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Index", e.getKey());
            MiniatureVoxel v = e.getValue();
            entry.put("State", NbtUtils.writeBlockState(v.state()));
            entry.putShort("Pixel", v.pixel());
            entry.putBoolean("Sampled", v.sampled());
            list.add(entry);
        }
        tag.put("Voxels", list);
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

        Map<Integer, MiniatureVoxel> next = new HashMap<>();
        ListTag list = tag.getList("Voxels", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int idx = entry.getInt("Index");
            BlockState state = NbtUtils.readBlockState(
                    registries.lookupOrThrow(Registries.BLOCK), entry.getCompound("State"));
            short pixel = entry.getShort("Pixel");
            boolean sampled = entry.getBoolean("Sampled");
            next.put(idx, new MiniatureVoxel(state, pixel, sampled));
        }
        voxels = Map.copyOf(next);
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