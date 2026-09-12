package com.archetipe.microworld.item;

import com.archetipe.microworld.Microworld;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

public class ShrinkRayItem extends Item {

    private static final double SIZE_FACTOR = 1.0 / 16.0;
    private static final double SIZE_AMOUNT = SIZE_FACTOR - 1.0;

    // Movement speed gets a small boost above the pure size ratio so
    // traversal doesn't feel glacial. 1.5x the pure 1/16 speed, i.e.
    // 0.09375x of normal.
    private static final double SPEED_FACTOR = SIZE_FACTOR;
    private static final double SPEED_AMOUNT = SPEED_FACTOR - 1.0;

    private static final double PHYSICS_FACTOR = 1.0 / 8.0;
    private static final double PHYSICS_AMOUNT = PHYSICS_FACTOR - 1.0;

    public static final ResourceLocation SHRINK_ID =
            ResourceLocation.fromNamespaceAndPath(Microworld.MODID, "shrink_modifier");

    // Scaled by SIZE_FACTOR. Movement speed removed -- it has its own list.
    private static final List<Holder<Attribute>> SIZE_ATTRIBUTES = List.of(
            Attributes.SCALE,
            Attributes.BLOCK_INTERACTION_RANGE,
            Attributes.ENTITY_INTERACTION_RANGE,
            Attributes.STEP_HEIGHT,
            Attributes.SAFE_FALL_DISTANCE
    );

    private static final List<Holder<Attribute>> SPEED_ATTRIBUTES = List.of(
            Attributes.MOVEMENT_SPEED
    );

    private static final List<Holder<Attribute>> PHYSICS_ATTRIBUTES = List.of(
            Attributes.GRAVITY,
            Attributes.JUMP_STRENGTH
    );

    public ShrinkRayItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        if (isShrunk(player)) {
            removeShrink(player);
        } else {
            applyShrink(player);
        }
        return InteractionResultHolder.success(stack);
    }

    public static boolean isShrunk(Player player) {
        AttributeInstance inst = player.getAttribute(Attributes.SCALE);
        return inst != null && inst.getModifier(SHRINK_ID) != null;
    }

    public static void applyShrink(Player player) {
        applyTo(player, SIZE_ATTRIBUTES, SIZE_AMOUNT);
        applyTo(player, SPEED_ATTRIBUTES, SPEED_AMOUNT);
        applyTo(player, PHYSICS_ATTRIBUTES, PHYSICS_AMOUNT);
    }

    public static void removeShrink(Player player) {
        removeFrom(player, SIZE_ATTRIBUTES);
        removeFrom(player, SPEED_ATTRIBUTES);
        removeFrom(player, PHYSICS_ATTRIBUTES);
    }

    private static void applyTo(Player player, List<Holder<Attribute>> attrs, double amount) {
        for (Holder<Attribute> attr : attrs) {
            AttributeInstance inst = player.getAttribute(attr);
            if (inst == null) continue;
            AttributeModifier mod = new AttributeModifier(
                    SHRINK_ID,
                    amount,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE
            );
            inst.addOrReplacePermanentModifier(mod);
        }
    }

    private static void removeFrom(Player player, List<Holder<Attribute>> attrs) {
        for (Holder<Attribute> attr : attrs) {
            AttributeInstance inst = player.getAttribute(attr);
            if (inst != null) inst.removeModifier(SHRINK_ID);
        }
    }
}