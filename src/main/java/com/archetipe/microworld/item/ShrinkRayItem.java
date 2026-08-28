package com.archetipe.microworld.item;

import com.archetipe.microworld.client.world.BlockPixelSampler; // client‑only
import com.archetipe.microworld.network.NetworkHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class ShrinkRayItem extends Item {

    private static final int SCALE = 16;
    private static final int HEIGHT_OFFSET = 20;

    public ShrinkRayItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockState clickedState = level.getBlockState(clickedPos);
        BlockPos origin = clickedPos.above(HEIGHT_OFFSET);

        if (level.isClientSide) {
            if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
                short[] pixelData = BlockPixelSampler.sampleBlock(
                        clickedState,
                        SCALE,
                        origin.asLong(),
                        origin,
                        level
                );
                NetworkHelper.sendMicroWorldData(origin, clickedState, SCALE, pixelData, clickedPos);
            }
        }

        return InteractionResult.SUCCESS;
    }
}