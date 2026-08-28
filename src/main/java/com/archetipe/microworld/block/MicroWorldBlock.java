package com.archetipe.microworld.block;

import com.archetipe.microworld.block.entity.MicroWorldBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MicroWorldBlock extends Block implements EntityBlock {

    public static final IntegerProperty DIR = IntegerProperty.create("dir", 0, 5);
    public static final IntegerProperty PX = IntegerProperty.create("px", 0, 15);
    public static final IntegerProperty PY = IntegerProperty.create("py", 0, 15);

    public MicroWorldBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.defaultBlockState()
                .setValue(DIR, 0)
                .setValue(PX, 0)
                .setValue(PY, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DIR, PX, PY);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MicroWorldBlockEntity(pos, state);
    }

    // ------ Lighting ------
    // MicroWorldBlocks are rendered as an enlarged reconstruction of a single
    // source block, split across potentially thousands of individual
    // instances. Whether a given voxel actually draws anything is a purely
    // client-side, per-resource-pack rendering decision (see
    // MicroWorldBakedModel), which the server has no reliable way to know --
    // two players with different resource packs could disagree on which
    // voxels are "invisible". Rather than trying to keep server-side light
    // blocking in sync with a client-only rendering fact, MicroWorldBlocks
    // simply never block light at all, regardless of what they render as.
    // This avoids dark patches/false "shadows" appearing around voxels that
    // happen to render as invisible.
    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    // ------ Destruction & Crown Transfer ------
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MicroWorldBlockEntity mwbe) {
                if (mwbe.isMaster()) {
                    mwbe.transferCrown(level, pos);
                } else {
                    mwbe.notifyMasterPixelRemoved();
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    // ------ Behaviour Delegation ------
    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MicroWorldBlockEntity mwbe) {
            mwbe.delegateStepOn(level, pos, state, entity);
        } else {
            super.stepOn(level, pos, state, entity);
        }
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MicroWorldBlockEntity mwbe) {
            mwbe.delegateEntityInside(level, pos, state, entity);
        } else {
            super.entityInside(state, level, pos, entity);
        }
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MicroWorldBlockEntity mwbe) {
            mwbe.delegateRandomTick(level, pos, state, random);
        } else {
            super.randomTick(state, level, pos, random);
        }
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        ServerLevel level = builder.getLevel();
        if (level == null) return super.getDrops(state, builder);

        Vec3 origin = builder.getParameter(LootContextParams.ORIGIN);
        if (origin == null) return super.getDrops(state, builder);
        BlockPos pos = BlockPos.containing(origin);

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MicroWorldBlockEntity mwbe) {
            return mwbe.getPixelDrops(state, builder);
        }
        return super.getDrops(state, builder);
    }
}