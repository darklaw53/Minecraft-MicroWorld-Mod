package com.archetipe.microworld.block;

import com.archetipe.microworld.block.entity.MiniatureBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class MiniatureBlock extends Block implements EntityBlock {

    public MiniatureBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MiniatureBlockEntity(pos, state);
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MiniatureBlockEntity mini) {
            return mini.getCollisionShape();
        }
        return Shapes.empty();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MiniatureBlockEntity mini) {
            return mini.getCollisionShape();
        }
        return Shapes.empty();
    }

    /**
     * Without this override, MC caches a "full cube" answer for this block
     * state at construction time (using an empty getter, where the BE lookup
     * fails), and every subsequent collision query takes a fast path that
     * returns the full cube instead of calling getCollisionShape(). Returning
     * false here forces the engine to query the shape per-position.
     */
    @Override
    public boolean isCollisionShapeFullBlock(BlockState state, BlockGetter level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MiniatureBlockEntity mini) {
            VoxelShape shape = mini.getCollisionShape();
            return !shape.isEmpty() && Shapes.block().equals(shape);
        }
        return false;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MiniatureBlockEntity mini) {
            mini.broadcastUpdate();
        }
    }
}