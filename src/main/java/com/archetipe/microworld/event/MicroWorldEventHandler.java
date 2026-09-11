package com.archetipe.microworld.event;

import com.archetipe.microworld.Microworld;
import com.archetipe.microworld.dimension.MicroWorldFloodFillScheduler;
import com.archetipe.microworld.dimension.ModDimensions;
import com.archetipe.microworld.util.CoordinateMapper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

@EventBusSubscriber(modid = Microworld.MODID)
public class MicroWorldEventHandler {

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!serverLevel.dimension().equals(ModDimensions.MICRO_WORLD_LEVEL)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        MinecraftServer server = serverLevel.getServer();
        if (server == null) return;
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        ChunkPos pos = chunk.getPos();
        int targetOy = resolveTargetOy(serverLevel, overworld);

        // One overworld block X/Z maps 1:1 onto one microworld chunk X/Z
        // (see MicroWorldFloodFillScheduler), so the chunk's own
        // coordinates ARE the overworld ox/oz to seed at.
        MicroWorldFloodFillScheduler.seed(new BlockPos(pos.x, targetOy, pos.z));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel microLevel = server.getLevel(ModDimensions.MICRO_WORLD_LEVEL);
        if (microLevel == null) return;
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        MicroWorldFloodFillScheduler.tick(microLevel, overworld);
    }

    /**
     * Where to start this chunk's very first seed cell, vertically. Prefers
     * the actual player's altitude; falls back to ShrinkRayItem's teleport
     * hint for the very first chunk (built before player.teleportTo() has
     * run yet -- see MicroWorldFloodFillScheduler for why); only falls back
     * to the overworld's mid-height if neither is available.
     */
    private static int resolveTargetOy(ServerLevel microLevel, ServerLevel overworld) {
        List<ServerPlayer> players = microLevel.players();
        if (!players.isEmpty()) {
            return (int) Math.floor(players.get(0).getY() / CoordinateMapper.SCALE);
        }
        Integer hint = MicroWorldFloodFillScheduler.peekTeleportTargetOy();
        if (hint != null) {
            return hint;
        }
        return (overworld.getMinBuildHeight() + overworld.getMaxBuildHeight()) / 2;
    }
}