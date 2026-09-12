package com.archetipe.microworld.network;

import com.archetipe.microworld.Microworld;
import com.archetipe.microworld.block.entity.MiniatureBlockEntity;
import com.archetipe.microworld.block.entity.MiniatureVoxel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

public record BreakSubVoxelPayload(BlockPos pos, int subX, int subY, int subZ)
        implements CustomPacketPayload {

    public static final Type<BreakSubVoxelPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Microworld.MODID, "break_sub_voxel"));

    public static final StreamCodec<FriendlyByteBuf, BreakSubVoxelPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public void encode(FriendlyByteBuf buf, BreakSubVoxelPayload p) {
                    BlockPos.STREAM_CODEC.encode(buf, p.pos);
                    buf.writeByte(p.subX);
                    buf.writeByte(p.subY);
                    buf.writeByte(p.subZ);
                }
                @Override
                public BreakSubVoxelPayload decode(FriendlyByteBuf buf) {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    return new BreakSubVoxelPayload(pos,
                            buf.readByte(), buf.readByte(), buf.readByte());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnServer(BreakSubVoxelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            if (!level.isLoaded(payload.pos())) return;
            if (player.blockPosition().distSqr(payload.pos()) > 64) return;

            BlockEntity be = level.getBlockEntity(payload.pos());
            if (!(be instanceof MiniatureBlockEntity mini)) return;

            int scale = mini.getScale();
            int sx = payload.subX(), sy = payload.subY(), sz = payload.subZ();
            if (sx < 0 || sy < 0 || sz < 0
                    || sx >= scale || sy >= scale || sz >= scale) return;

            int idx = (sy * scale + sz) * scale + sx;

            Map<Integer, MiniatureVoxel> old = mini.getVoxels();
            if (!old.containsKey(idx)) return;

            Map<Integer, MiniatureVoxel> next = new HashMap<>(old);
            next.remove(idx);

            // Remove from the megablock too.
            ResourceKey<Level> dimKey = mini.getSourceDimension();
            BlockPos microOrigin = mini.getSourceOrigin();
            if (dimKey != null && microOrigin != null) {
                ServerLevel micro = player.getServer().getLevel(dimKey);
                if (micro != null) {
                    BlockPos voxelPos = microOrigin.offset(sx, sy, sz);
                    micro.setBlock(voxelPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }

            if (next.isEmpty()) {
                level.removeBlock(payload.pos(), false);
            } else {
                mini.updateVoxels(next);
            }
        });
    }
}