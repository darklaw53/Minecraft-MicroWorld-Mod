package com.archetipe.microworld.event;

import com.archetipe.microworld.Microworld;
import com.archetipe.microworld.item.ShrinkRayItem;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = Microworld.MODID)
public class ShrinkEventHandler {

    private static final float CLIMB_LOOK_ANGLE = -20.0f;
    private static final double CLIMB_EXTRA = 0.025;
    private static final double AIR_HORIZONTAL_FRACTION = 0.5;

    private static final float BODY_YAW_RATE = 0.5f;

    private static final double SHRUNK_MOVEMENT_THRESHOLD = 0.0025 / 256.0;
    private static final float MAX_HEAD_TURN = 75.0f;

    private static final float WALK_DIST_EXTRA = 15.0f;

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && ShrinkRayItem.isShrunk(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();

        if (!ShrinkRayItem.isShrunk(player)) {
            return;
        }

        player.fallDistance = 0f;

        boolean onGround = player.onGround();
        boolean againstWall = !onGround && player.horizontalCollision;

        boolean inFluid = player.isInWater() || player.isInLava();
        boolean elytra = player.isFallFlying();

        if (!onGround && !againstWall && !inFluid && !elytra) {
            Vec3 delta = player.getDeltaMovement();

            double cap = player.getAttributeValue(Attributes.MOVEMENT_SPEED)
                    * AIR_HORIZONTAL_FRACTION;

            double h2 = delta.x * delta.x + delta.z * delta.z;

            if (h2 > cap * cap) {
                double factor = cap / Math.sqrt(h2);

                player.setDeltaMovement(
                        delta.x * factor,
                        delta.y,
                        delta.z * factor
                );
            }
        }

        if (!againstWall) {
            return;
        }

        double gravity = player.getAttributeValue(Attributes.GRAVITY);

        Vec3 delta = player.getDeltaMovement();
        double y;

        if (player.isCrouching()) {
            y = gravity - CLIMB_EXTRA;
        } else if (player.getXRot() < CLIMB_LOOK_ANGLE) {
            y = gravity + CLIMB_EXTRA;
        } else {
            y = gravity;
        }

        player.setDeltaMovement(delta.x, y, delta.z);
    }

    @SubscribeEvent
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        Player player = event.getEntity();

        if (!ShrinkRayItem.isShrunk(player)) {
            return;
        }

        /*
         * Vanilla's walkDist advances according to the player's real movement.
         * A shrunk player moves roughly 1/16 as far, so without compensation
         * the walking animation and camera bob advance much too slowly.
         *
         * We compensate the phase here, but deliberately do not modify player.bob.
         * bob is an interpolated animation value, so multiplying it every tick
         * causes it to rapidly reach its maximum and produces excessive camera
         * movement.
         */
        if (player.level().isClientSide) {
            double dx = player.getX() - player.xo;
            double dz = player.getZ() - player.zo;

            float tickDist = (float) Math.sqrt(dx * dx + dz * dz);

            if (tickDist > 0.0f) {
                player.walkDist += tickDist * WALK_DIST_EXTRA;
            }
        }

        double dx = player.getX() - player.xo;
        double dz = player.getZ() - player.zo;
        double horizSq = dx * dx + dz * dz;

        if (horizSq < SHRUNK_MOVEMENT_THRESHOLD) {
            return;
        }

        float target = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;

        float delta = Mth.wrapDegrees(target - player.yBodyRot);

        player.yBodyRot += delta * BODY_YAW_RATE;

        float headDelta = Mth.wrapDegrees(player.getYRot() - player.yBodyRot);

        if (headDelta > MAX_HEAD_TURN) {
            player.yBodyRot = player.getYRot() - MAX_HEAD_TURN;
        } else if (headDelta < -MAX_HEAD_TURN) {
            player.yBodyRot = player.getYRot() + MAX_HEAD_TURN;
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }

        Player oldPlayer = event.getOriginal();
        Player newPlayer = event.getEntity();

        if (ShrinkRayItem.isShrunk(oldPlayer)) {
            ShrinkRayItem.applyShrink(newPlayer);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (!ShrinkRayItem.isShrunk(player)) return;
        event.setCanceled(true);
    }
}