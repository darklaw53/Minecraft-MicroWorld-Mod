package com.archetipe.microworld.item;

import com.archetipe.microworld.client.world.BlockPixelSampler;
import com.archetipe.microworld.network.NetworkHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class ShrinkRayItem extends Item {

    private static final String NBT_PENDING = "microworld_pending";
    private static final int SCALE = 16;

    public ShrinkRayItem(Properties properties) {
        super(properties);
    }

    private static boolean isPending(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getBoolean(NBT_PENDING);
    }

    private static void setPending(ItemStack stack, boolean pending) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putBoolean(NBT_PENDING, pending);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();

        if (level.isClientSide) {
            if (isPending(stack)) {
                // Second click: request placement adjacent to the clicked face.
                BlockPos target = context.getClickedPos().relative(context.getClickedFace());
                NetworkHelper.sendPlaceMiniature(target);
                setPending(stack, false);
                return InteractionResult.SUCCESS;
            }

            // First click: sample the block and send the megablock build.
            BlockPos pos = context.getClickedPos();
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) return InteractionResult.PASS;

            BlockPos microOrigin = new BlockPos(pos.getX() * SCALE, pos.getY() * SCALE, pos.getZ() * SCALE);
            short[] pixelData = BlockPixelSampler.sampleBlock(state, SCALE, pos.asLong(), pos, level);
            NetworkHelper.sendMicroWorldData(microOrigin, state, SCALE, pixelData, pos);
            setPending(stack, true);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }
}