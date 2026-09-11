package com.archetipe.microworld.network;

import com.archetipe.microworld.Microworld;
import com.archetipe.microworld.block.entity.MiniatureBlockEntity;
import com.archetipe.microworld.dimension.ModDimensions;
import com.archetipe.microworld.registry.ModBlocks;
import com.archetipe.microworld.util.PendingMegablock;
import com.archetipe.microworld.util.PendingMegablockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PlaceMiniaturePayload(BlockPos placePos) implements CustomPacketPayload {

    public static final Type<PlaceMiniaturePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Microworld.MODID, "place_miniature"));

    public static final StreamCodec<FriendlyByteBuf, PlaceMiniaturePayload> CODEC =
            new StreamCodec<>() {
                @Override
                public void encode(FriendlyByteBuf buf, PlaceMiniaturePayload p) {
                    BlockPos.STREAM_CODEC.encode(buf, p.placePos);
                }
                @Override
                public PlaceMiniaturePayload decode(FriendlyByteBuf buf) {
                    return new PlaceMiniaturePayload(BlockPos.STREAM_CODEC.decode(buf));
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnServer(PlaceMiniaturePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();

            PendingMegablock pending = PendingMegablockRegistry.get(player.getUUID());
            if (pending == null) return;

            BlockPos target = payload.placePos();
            BlockState existing = level.getBlockState(target);
            if (!existing.isAir() && !existing.canBeReplaced()) return;

            MinecraftServer server = player.getServer();
            if (server == null) return;
            ServerLevel source = server.getLevel(ModDimensions.MICRO_WORLD_LEVEL);
            if (source == null) return;

            BlockState miniState = ModBlocks.MINIATURE_BLOCK.get().defaultBlockState();
            level.setBlock(target, miniState, Block.UPDATE_ALL);

            BlockEntity be = level.getBlockEntity(target);
            if (be instanceof MiniatureBlockEntity mini) {
                // Remember where the source megablock lives so future refreshes
                // (if enabled) know where to look. Does NOT read the megablock.
                mini.setSourceLocation(source.dimension(), pending.origin(), pending.scale());
                // Populate voxels from the data the client already sent. Zero
                // world reads, zero chunk loads, ready this tick.
                mini.populateFromPixelData(pending.source(), pending.pixelData(), pending.scale());
            }

            PendingMegablockRegistry.clear(player.getUUID());
        });
    }
}