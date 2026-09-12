package com.archetipe.microworld.mixin;

import com.archetipe.microworld.item.ShrinkRayItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityWalkAnimationMixin {

    private static final float ANIMATION_BOOST = 16.0f;

    @ModifyVariable(
            method = "updateWalkAnimation",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private float microworld$boostWalkSpeed(float f) {
        if (!((Object) this instanceof Player player)) return f;
        if (!ShrinkRayItem.isShrunk(player)) return f;
        return f * ANIMATION_BOOST;
    }
}