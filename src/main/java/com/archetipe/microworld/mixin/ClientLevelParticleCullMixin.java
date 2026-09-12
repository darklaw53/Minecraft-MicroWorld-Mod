package com.archetipe.microworld.mixin;

import com.archetipe.microworld.item.ShrinkRayItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class ClientLevelParticleCullMixin {

    // Cull radius, in blocks, around the local player. Big enough to catch
    // player-foot particles (which spawn with a small random offset) but
    // small enough that a nearby torch's smoke isn't affected.
    private static final double CULL_RADIUS_SQ = 0.6 * 0.6;

    @Inject(
            method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void microworld$cullPlayerActionParticles(
            ParticleOptions options,
            double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed,
            CallbackInfo ci) {

        Player local = Minecraft.getInstance().player;
        if (local == null) return;
        if (!ShrinkRayItem.isShrunk(local)) return;
        if (!isPlayerActionParticle(options)) return;

        double cx = local.getX();
        double cy = local.getY() + local.getBbHeight() * 0.5;
        double cz = local.getZ();
        double dx = x - cx, dy = y - cy, dz = z - cz;
        if (dx * dx + dy * dy + dz * dz < CULL_RADIUS_SQ) {
            ci.cancel();
        }
    }

    private static boolean isPlayerActionParticle(ParticleOptions options) {
        ParticleType<?> type = options.getType();
        return type == ParticleTypes.CRIT
                || type == ParticleTypes.ENCHANTED_HIT
                || type == ParticleTypes.SWEEP_ATTACK
                || type == ParticleTypes.CLOUD
                || type == ParticleTypes.BLOCK;
    }
}