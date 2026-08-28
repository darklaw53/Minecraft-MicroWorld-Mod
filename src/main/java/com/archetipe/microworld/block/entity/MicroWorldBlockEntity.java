package com.archetipe.microworld.block.entity;

import com.archetipe.microworld.block.MicroWorldBlock;
import com.archetipe.microworld.registry.ModBlockEntities;
import com.archetipe.microworld.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import java.util.ArrayList;
import java.util.List;

public class MicroWorldBlockEntity extends BlockEntity {

    @OnlyIn(Dist.CLIENT)
    public static final ModelProperty<BlockPos> POSITION_PROPERTY =
            new ModelProperty<>();

    private int scale = 16;
    private BlockState originalState;
    private short[] pixelData;

    private BlockPos masterPos;
    private int index = -1;
    private BlockPos originalPos;

    // The corner (index 0) of the scale^3 sample cube. Only meaningful on the
    // master. Needed to convert pixelData indices back into world positions --
    // the master itself is NOT necessarily at this corner (e.g. for fences,
    // stairs, and other partial-shape source blocks, the corner voxel is often
    // unoccupied, so the master ends up wherever the first occupied voxel was).
    private BlockPos structureOrigin;

    private int grassColor = -1;

    public MicroWorldBlockEntity(
            BlockPos pos,
            BlockState state
    ) {
        super(
                ModBlockEntities.MICRO_WORLD_BLOCK_ENTITY.get(),
                pos,
                state
        );
    }

    public boolean isMaster() {
        return masterPos != null &&
                masterPos.equals(worldPosition);
    }

    public BlockPos getMasterPos() {
        return masterPos;
    }

    public void setMasterPos(BlockPos masterPos) {
        this.masterPos = masterPos;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getScale() {
        return scale;
    }

    public void setScale(int scale) {
        this.scale = scale;
    }

    public BlockState getOriginalState() {
        return originalState;
    }

    public void setOriginalState(BlockState originalState) {
        this.originalState = originalState;
    }

    public short[] getPixelData() {
        return pixelData;
    }

    public void setPixelData(short[] pixelData) {
        this.pixelData = pixelData;
    }

    public BlockPos getOriginalPos() {
        return originalPos;
    }

    public void setOriginalPos(BlockPos originalPos) {
        this.originalPos = originalPos;
    }

    public BlockPos getStructureOrigin() {
        return structureOrigin;
    }

    public void setStructureOrigin(BlockPos structureOrigin) {
        this.structureOrigin = structureOrigin;
    }

    public int getGrassColor() {
        return grassColor;
    }

    public void setGrassColor(int grassColor) {
        this.grassColor = grassColor;
    }

    public void setMasterData(
            int scale,
            BlockState original,
            short[] data
    ) {
        this.scale = scale;
        this.originalState = original;
        this.pixelData = data;

        setChanged();

        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(
                    worldPosition,
                    getBlockState(),
                    getBlockState(),
                    Block.UPDATE_CLIENTS
            );
        }

        // Force model data update on client
        if (level != null && level.isClientSide) {
            requestModelDataUpdate();
            level.sendBlockUpdated(
                    worldPosition,
                    getBlockState(),
                    getBlockState(),
                    Block.UPDATE_CLIENTS
            );
        }
    }

    public void delegateStepOn(
            Level level,
            BlockPos pos,
            BlockState state,
            Entity entity
    ) {
        MicroWorldBlockEntity master = getMasterBE();
        if (master != null && master.originalState != null &&
                master.originalState.getBlock() == Blocks.MAGMA_BLOCK) {
            entity.hurt(
                    level.damageSources().hotFloor(),
                    5.0f
            );
        }
    }

    public void delegateEntityInside(
            Level level,
            BlockPos pos,
            BlockState state,
            Entity entity
    ) {
        MicroWorldBlockEntity master = getMasterBE();
        if (master != null && master.originalState != null) {
            master.originalState.entityInside(
                    level,
                    pos,
                    entity
            );
        }
    }

    public void delegateRandomTick(
            net.minecraft.server.level.ServerLevel level,
            BlockPos pos,
            BlockState state,
            RandomSource random
    ) {
        MicroWorldBlockEntity master = getMasterBE();
        if (master != null && master.originalState != null) {
            master.originalState.randomTick(
                    level,
                    pos,
                    random
            );
        }
    }

    public List<ItemStack> getPixelDrops(
            BlockState state,
            LootParams.Builder builder
    ) {
        MicroWorldBlockEntity master = getMasterBE();
        if (master == null || master.originalState == null) {
            return new ArrayList<>();
        }
        return master.originalState.getDrops(builder);
    }

    public MicroWorldBlockEntity getMasterBE() {
        if (level == null || masterPos == null) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(masterPos);
        if (be instanceof MicroWorldBlockEntity) {
            return (MicroWorldBlockEntity) be;
        }
        return null;
    }

    public void notifyMasterPixelRemoved() {
        MicroWorldBlockEntity master = getMasterBE();
        if (master != null && master.pixelData != null &&
                index >= 0 && index < master.pixelData.length) {
            master.pixelData[index] = 0;
            master.setChanged();
        }
    }

    public void transferCrown(
            Level level,
            BlockPos oldMasterPos
    ) {
        if (pixelData == null || structureOrigin == null) {
            return;
        }

        for (int i = 0; i < pixelData.length; i++) {
            if (pixelData[i] != 0) {
                BlockPos newMasterPos = getSlavePos(i);
                if (newMasterPos.equals(oldMasterPos)) continue;
                BlockState oldSlaveState = level.getBlockState(newMasterPos);

                if (oldSlaveState.getBlock() instanceof MicroWorldBlock) {
                    int dir = oldSlaveState.getValue(MicroWorldBlock.DIR);
                    int px = oldSlaveState.getValue(MicroWorldBlock.PX);
                    int py = oldSlaveState.getValue(MicroWorldBlock.PY);

                    level.removeBlock(newMasterPos, false);

                    BlockState newMasterState = ModBlocks.MICRO_WORLD_BLOCK.get()
                            .defaultBlockState()
                            .setValue(MicroWorldBlock.DIR, dir)
                            .setValue(MicroWorldBlock.PX, px)
                            .setValue(MicroWorldBlock.PY, py);

                    level.setBlock(newMasterPos, newMasterState, Block.UPDATE_ALL);

                    BlockEntity be = level.getBlockEntity(newMasterPos);
                    if (be instanceof MicroWorldBlockEntity newMaster) {
                        newMaster.setMasterData(scale, originalState, pixelData);
                        newMaster.setMasterPos(newMasterPos);
                        newMaster.setIndex(-1);
                        newMaster.setOriginalPos(originalPos);
                        newMaster.setStructureOrigin(structureOrigin);
                        newMaster.setGrassColor(grassColor);
                        newMaster.setChanged();

                        updateAllMasterPointers(level, newMasterPos);
                    }
                    return;
                }
            }
        }
    }

    private void updateAllMasterPointers(
            Level level,
            BlockPos newMaster
    ) {
        for (int x = 0; x < scale; x++) {
            for (int y = 0; y < scale; y++) {
                for (int z = 0; z < scale; z++) {
                    BlockPos slavePos = structureOrigin.offset(x, y, z);
                    if (slavePos.equals(newMaster)) continue;

                    BlockEntity be = level.getBlockEntity(slavePos);
                    if (be instanceof MicroWorldBlockEntity slaveBE) {
                        if (slaveBE.getMasterPos() != null &&
                                !slaveBE.getMasterPos().equals(newMaster)) {
                            slaveBE.setMasterPos(newMaster);
                            slaveBE.setChanged();
                        }
                    }
                }
            }
        }
    }

    /**
     * Shared packing convention (also used by BlockPixelSampler and
     * EnlargedBlockGenerator): idx = (y * scale + z) * scale + x, relative to
     * structureOrigin -- NOT relative to this block's own position, since the
     * master is not necessarily at the corner of the cube.
     */
    private BlockPos getSlavePos(int index) {
        int x = index % scale;
        int rem = index / scale;
        int z = rem % scale;
        int y = rem / scale;
        return structureOrigin.offset(x, y, z);
    }

    @Override
    protected void saveAdditional(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        super.saveAdditional(tag, registries);

        if (masterPos != null) {
            tag.putLong("MasterPos", masterPos.asLong());
        }
        tag.putInt("Index", index);
        tag.putInt("Scale", scale);

        if (originalState != null) {
            tag.put("OriginalState", NbtUtils.writeBlockState(originalState));
        }

        if (pixelData != null) {
            int[] intData = new int[pixelData.length];
            for (int i = 0; i < pixelData.length; i++) {
                intData[i] = pixelData[i] & 0xFFFF;
            }
            tag.putIntArray("PixelData", intData);
        }

        if (originalPos != null) {
            tag.putLong("OriginalPos", originalPos.asLong());
        }

        if (structureOrigin != null) {
            tag.putLong("StructureOrigin", structureOrigin.asLong());
        }

        if (grassColor != -1) {
            tag.putInt("GrassColor", grassColor);
        }
    }

    @Override
    protected void loadAdditional(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        super.loadAdditional(tag, registries);

        if (tag.contains("MasterPos")) {
            masterPos = BlockPos.of(tag.getLong("MasterPos"));
        }
        index = tag.getInt("Index");
        scale = tag.getInt("Scale");

        if (tag.contains("OriginalState")) {
            originalState = NbtUtils.readBlockState(
                    registries.lookupOrThrow(Registries.BLOCK),
                    tag.getCompound("OriginalState")
            );
        }

        if (tag.contains("PixelData")) {
            int[] intData = tag.getIntArray("PixelData");
            pixelData = new short[intData.length];
            for (int i = 0; i < intData.length; i++) {
                pixelData[i] = (short) intData[i];
            }
        }

        if (tag.contains("OriginalPos")) {
            originalPos = BlockPos.of(tag.getLong("OriginalPos"));
        }

        if (tag.contains("StructureOrigin")) {
            structureOrigin = BlockPos.of(tag.getLong("StructureOrigin"));
        }

        if (tag.contains("GrassColor")) {
            grassColor = tag.getInt("GrassColor");
        }

        // After loading data, force model data update and rerender on client
        if (level != null && level.isClientSide) {
            requestModelDataUpdate();
            level.sendBlockUpdated(
                    worldPosition,
                    getBlockState(),
                    getBlockState(),
                    Block.UPDATE_CLIENTS
            );
        }
    }

    @Override
    public CompoundTag getUpdateTag(
            HolderLookup.Provider registries
    ) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // Called when client receives the update packet from server
    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        // After loading from the update tag, request model update and rerender
        if (level != null && level.isClientSide) {
            requestModelDataUpdate();
            level.sendBlockUpdated(
                    worldPosition,
                    getBlockState(),
                    getBlockState(),
                    Block.UPDATE_CLIENTS
            );
        }
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public ModelData getModelData() {
        return ModelData.builder()
                .with(POSITION_PROPERTY, worldPosition)
                .build();
    }

    public int getDir() {
        return getBlockState().getValue(MicroWorldBlock.DIR);
    }

    public Direction getReferenceDirection() {
        return Direction.from3DDataValue(getDir());
    }

    public int getPx() {
        return getBlockState().getValue(MicroWorldBlock.PX);
    }

    public int getPy() {
        return getBlockState().getValue(MicroWorldBlock.PY);
    }

    public void setDir(int dir) {
        // no-op
    }

    public void setPx(int px) {
        // no-op
    }

    public void setPy(int py) {
        // no-op
    }
}