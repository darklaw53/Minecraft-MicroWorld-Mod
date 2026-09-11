package com.archetipe.microworld.block.entity;

import com.archetipe.microworld.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

public class MagnifiedBlockEntity extends BlockEntity {

    @OnlyIn(Dist.CLIENT)
    public static final ModelProperty<BlockPos> POSITION_PROPERTY = new ModelProperty<>();

    private BlockState originalState;

    public MagnifiedBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MAGNIFIED_BLOCK_ENTITY.get(), pos, state);
    }

    public BlockState getOriginalState() {
        return originalState;
    }

    public void setOriginalState(BlockState originalState) {
        this.originalState = originalState;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (originalState != null) {
            tag.put("OriginalState", NbtUtils.writeBlockState(originalState));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("OriginalState")) {
            originalState = NbtUtils.readBlockState(
                    registries.lookupOrThrow(Registries.BLOCK),
                    tag.getCompound("OriginalState")
            );
        }
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
        if (level != null && level.isClientSide) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public ModelData getModelData() {
        return ModelData.builder().with(POSITION_PROPERTY, worldPosition).build();
    }
}