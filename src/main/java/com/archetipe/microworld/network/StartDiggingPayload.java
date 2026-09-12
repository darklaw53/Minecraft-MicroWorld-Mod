package com.archetipe.microworld.network;

import com.archetipe.microworld.Microworld;
import com.archetipe.microworld.block.MiniatureBlock;
import com.archetipe.microworld.block.entity.MiniatureBlockEntity;
import com.archetipe.microworld.dimension.ModDimensions;
import com.archetipe.microworld.registry.ModBlocks;
import com.archetipe.microworld.world.EnlargedBlockGenerator;
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

public record StartDiggingPayload(
        BlockPos pos,
        BlockState sourceState,
        int scale,
        short[] pixelData,
        BlockPos originalPos
) implements CustomPacketPayload {

    private static final int MAX_SCALE = 16;

    public static final Type<StartDiggingPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Microworld.MODID, "start_digging"));

    public static final StreamCodec<FriendlyByteBuf, StartDiggingPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public void encode(FriendlyByteBuf buf, StartDiggingPayload p) {
                    BlockPos.STREAM_CODEC.encode(buf, p.pos);
                    buf.writeInt(Block.getId(p.sourceState));
                    buf.writeInt(p.scale);
                    buf.writeInt(p.pixelData.length);
                    for (short s : p.pixelData) buf.writeShort(s);
                    BlockPos.STREAM_CODEC.encode(buf, p.originalPos);
                }
                @Override
                public StartDiggingPayload decode(FriendlyByteBuf buf) {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    BlockState state = Block.stateById(buf.readInt());
                    int scale = buf.readInt();
                    if (scale <= 0 || scale > MAX_SCALE) {
                        throw new IllegalArgumentException("bad scale " + scale);
                    }
                    int len = buf.readInt();
                    if (len < 0 || len > MAX_SCALE * MAX_SCALE * MAX_SCALE) {
                        throw new IllegalArgumentException("bad length " + len);
                    }
                    short[] data = new short[len];
                    for (int i = 0; i < len; i++) data[i] = buf.readShort();
                    BlockPos orig = BlockPos.STREAM_CODEC.decode(buf);
                    return new StartDiggingPayload(pos, state, scale, data, orig);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnServer(StartDiggingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();

            BlockPos pos = payload.pos();
            if (!level.isLoaded(pos)) return;
            if (player.blockPosition().distSqr(pos) > 64) return;

            BlockState current = level.getBlockState(pos);
            if (current.isAir()) return;
            if (current.getBlock() instanceof MiniatureBlock) return;

            MinecraftServer server = player.getServer();
            if (server == null) return;
            ServerLevel microLevel = server.getLevel(ModDimensions.MICRO_WORLD_LEVEL);
            if (microLevel == null) return;

            int scale = payload.scale();
            BlockPos microOrigin = new BlockPos(
                    pos.getX() * scale, pos.getY() * scale, pos.getZ() * scale);

            EnlargedBlockGenerator.buildStructure(
                    microLevel, microOrigin, payload.sourceState(),
                    scale, payload.pixelData(), payload.originalPos());

            level.setBlock(pos, ModBlocks.MINIATURE_BLOCK.get().defaultBlockState(),
                    Block.UPDATE_ALL);

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MiniatureBlockEntity mini) {
                mini.setSourceLocation(microLevel.dimension(), microOrigin, scale);
                mini.populateFromPixelData(
                        payload.sourceState(), payload.pixelData(), scale,
                        microLevel, microOrigin);
            }
        });
    }
}