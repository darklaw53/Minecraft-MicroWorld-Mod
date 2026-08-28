package com.archetipe.microworld.network;

import com.archetipe.microworld.Microworld;
import com.archetipe.microworld.world.EnlargedBlockGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Method;

import static com.archetipe.microworld.Microworld.MODID;

public record MicroWorldDataPayload(
        BlockPos origin,
        BlockState sourceState,
        int scale,
        short[] pixelData,
        BlockPos originalPos
) implements CustomPacketPayload {

    // Must match the SCALE used on the client (ShrinkRayItem / BlockPixelSampler).
    // Anything the client claims beyond this is rejected rather than trusted.
    private static final int MAX_SCALE = 16;

    public static final Type<MicroWorldDataPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "micro_world_data"));

    public static final StreamCodec<FriendlyByteBuf, MicroWorldDataPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public void encode(FriendlyByteBuf buf, MicroWorldDataPayload payload) {
                    BlockPos.STREAM_CODEC.encode(buf, payload.origin);
                    buf.writeInt(Block.getId(payload.sourceState));
                    buf.writeInt(payload.scale);

                    // Manual short array serialisation (portable)
                    short[] arr = payload.pixelData;
                    buf.writeInt(arr.length);
                    for (short s : arr) {
                        buf.writeShort(s);
                    }

                    BlockPos.STREAM_CODEC.encode(buf, payload.originalPos);
                }

                @Override
                public MicroWorldDataPayload decode(FriendlyByteBuf buf) {
                    BlockPos origin = BlockPos.STREAM_CODEC.decode(buf);
                    BlockState state = Block.stateById(buf.readInt());
                    int scale = buf.readInt();

                    int len = buf.readInt();

                    // Reject before allocating: a malicious/buggy client could send an
                    // arbitrary length here. Cap it at what a MAX_SCALE^3 cube could ever
                    // legitimately need.
                    int maxLen = MAX_SCALE * MAX_SCALE * MAX_SCALE;
                    if (len < 0 || len > maxLen) {
                        throw new IllegalArgumentException(
                                "MicroWorldDataPayload: rejected pixelData length " + len
                        );
                    }

                    short[] pixelData = new short[len];
                    for (int i = 0; i < len; i++) {
                        pixelData[i] = buf.readShort();
                    }

                    BlockPos originalPos = BlockPos.STREAM_CODEC.decode(buf);
                    return new MicroWorldDataPayload(origin, state, scale, pixelData, originalPos);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // ---- Server-side handler ----
    public static void handleOnServer(MicroWorldDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = getPlayerFromContext(context);
            if (player == null) {
                // Should never happen on a server-bound packet
                throw new IllegalStateException("Unable to retrieve ServerPlayer from context");
            }

            // Validate scale and consistency between scale and pixelData length before
            // doing anything with the data. Never trust these values from the client.
            if (payload.scale <= 0 || payload.scale > MAX_SCALE) {
                Microworld.LOGGER.warn(
                        "Rejected MicroWorldDataPayload from {}: invalid scale {}",
                        player.getGameProfile().getName(),
                        payload.scale
                );
                return;
            }

            int expectedLength = payload.scale * payload.scale * payload.scale;
            if (payload.pixelData.length != expectedLength) {
                Microworld.LOGGER.warn(
                        "Rejected MicroWorldDataPayload from {}: pixelData length {} does not match scale {} (expected {})",
                        player.getGameProfile().getName(),
                        payload.pixelData.length,
                        payload.scale,
                        expectedLength
                );
                return;
            }

            // Get the server level (fallback for different method names)
            ServerLevel level;
            try {
                level = player.serverLevel();
            } catch (NoSuchMethodError e) {
                level = (ServerLevel) player.level();
            }

            EnlargedBlockGenerator.buildStructure(
                    level,
                    payload.origin,
                    payload.sourceState,
                    payload.scale,
                    payload.pixelData,
                    payload.originalPos
            );
        });
    }

    // ---- Reflection-based player extraction ----
    private static ServerPlayer getPlayerFromContext(IPayloadContext context) {
        // Try known method names in order of popularity
        String[] methodNames = {"player", "getPlayer", "getSender"};
        for (String name : methodNames) {
            try {
                Method method = IPayloadContext.class.getMethod(name);
                Object result = method.invoke(context);
                if (result instanceof ServerPlayer) {
                    return (ServerPlayer) result;
                }
            } catch (Exception ignored) {
                // Try the next method name
            }
        }
        return null;
    }
}