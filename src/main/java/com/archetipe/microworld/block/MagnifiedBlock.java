package com.archetipe.microworld.block;

import com.archetipe.microworld.block.entity.MagnifiedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The "hologram" tier's single real block: physically a normal 1x1x1 solid
 * cube, but its custom baked model (MagnifiedBakedModel) renders a full
 * 16x16x16 cube using the referenced source block's own texture, filling
 * exactly one chunk section -- see MagnifiedBakedModel for why staying
 * within one section is what keeps this safe to render at all.
 */
public class MagnifiedBlock extends Block implements EntityBlock {

    public MagnifiedBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MagnifiedBlockEntity(pos, state);
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        // One real block standing in for an entire 16x16x16 region should not
        // cast a disproportionate giant shadow.
        return 0;
    }
}