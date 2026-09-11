package com.archetipe.microworld.world;

import com.archetipe.microworld.block.MicroWorldBlock;
import com.archetipe.microworld.block.entity.MicroWorldBlockEntity;
import com.archetipe.microworld.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class EnlargedBlockGenerator {

    private EnlargedBlockGenerator() {}

    public static void buildStructure(
            ServerLevel level,
            BlockPos origin,
            BlockState sourceState,
            int scale,
            short[] pixelData,
            BlockPos originalPos
    ) {
        int masterIndex = -1;
        for (int i = 0; i < pixelData.length; i++) {
            if (pixelData[i] != 0) {
                masterIndex = i;
                break;
            }
        }
        if (masterIndex == -1) return;

        BlockPos masterPos = indexToPos(origin, masterIndex, scale);

        int masterDir = (pixelData[masterIndex] >> 8) & 0x7;
        int masterPX  = (pixelData[masterIndex] >> 4) & 0xF;
        int masterPY  = pixelData[masterIndex] & 0xF;

        BlockState masterState = ModBlocks.MICRO_WORLD_BLOCK.get()
                .defaultBlockState()
                .setValue(MicroWorldBlock.DIR, masterDir)
                .setValue(MicroWorldBlock.PX, masterPX)
                .setValue(MicroWorldBlock.PY, masterPY);

        level.setBlock(masterPos, masterState, Block.UPDATE_ALL);

        BlockEntity masterEntity = level.getBlockEntity(masterPos);
        if (masterEntity instanceof MicroWorldBlockEntity masterBE) {
            masterBE.setMasterData(scale, sourceState, pixelData);
            masterBE.setMasterPos(masterPos);
            masterBE.setIndex(-1);
            masterBE.setOriginalPos(originalPos);
            masterBE.setStructureOrigin(origin);

            if (sourceState.is(Blocks.GRASS_BLOCK) && originalPos != null) {
                int grassColor = level.getBiome(originalPos)
                        .value()
                        .getGrassColor(originalPos.getX(), originalPos.getZ());
                grassColor |= 0xFF000000;
                masterBE.setGrassColor(grassColor);
            }

            masterBE.setDir(masterDir);
            masterBE.setPx(masterPX);
            masterBE.setPy(masterPY);
            masterBE.setChanged();
            level.sendBlockUpdated(masterPos, masterState, masterState, Block.UPDATE_CLIENTS);
        }

        for (int idx = 0; idx < pixelData.length; idx++) {
            if (idx == masterIndex) continue;
            if (pixelData[idx] == 0) continue;

            int dir = (pixelData[idx] >> 8) & 0x7;
            int px  = (pixelData[idx] >> 4) & 0xF;
            int py  = pixelData[idx] & 0xF;

            BlockPos slavePos = indexToPos(origin, idx, scale);
            BlockState slaveState = ModBlocks.MICRO_WORLD_BLOCK.get()
                    .defaultBlockState()
                    .setValue(MicroWorldBlock.DIR, dir)
                    .setValue(MicroWorldBlock.PX, px)
                    .setValue(MicroWorldBlock.PY, py);

            level.setBlock(slavePos, slaveState, Block.UPDATE_ALL);

            BlockEntity be = level.getBlockEntity(slavePos);
            if (be instanceof MicroWorldBlockEntity slaveBE) {
                slaveBE.setMasterPos(masterPos);
                slaveBE.setIndex(idx);
                slaveBE.setDir(dir);
                slaveBE.setPx(px);
                slaveBE.setPy(py);
                slaveBE.setChanged();
            }
        }
    }

    private static BlockPos indexToPos(BlockPos origin, int index, int scale) {
        int x = index % scale;
        int rem = index / scale;
        int z = rem % scale;
        int y = rem / scale;
        return origin.offset(x, y, z);
    }
}