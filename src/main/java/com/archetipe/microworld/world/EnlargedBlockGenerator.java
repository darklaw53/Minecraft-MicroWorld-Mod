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

/**
 * Server‑only class that builds the enlarged micro‑block structure from
 * a pre‑computed packed pixel data array.
 */
public final class EnlargedBlockGenerator {

    private EnlargedBlockGenerator() {}

    /**
     * Places the master and slave blocks for the enlarged structure.
     *
     * @param level       the server level
     * @param origin      the corner (index 0) of the scale^3 sample cube
     * @param sourceState the original block state (for reference)
     * @param scale       the enlargement factor
     * @param pixelData   packed data from the client (short array, length scale^3)
     * @param originalPos the original position (used for biome colours)
     */
    public static void buildStructure(
            ServerLevel level,
            BlockPos origin,
            BlockState sourceState,
            int scale,
            short[] pixelData,
            BlockPos originalPos
    ) {
        // The master block is the one MicroWorldBlockEntity that actually holds
        // scale/originalState/pixelData for the whole structure; every other
        // placed block just points at it. It used to always be placed at
        // pixelData[0] (the (0,0,0) corner of the cube), which is fine for a
        // full-cube source block but is almost never occupied for a partial-shape
        // source (fences, stairs, slabs, walls...). When pixelData[0] was 0, no
        // master was ever created, yet the slave-placement loop below still ran
        // and happily placed slave blocks pointing at an empty masterPos with no
        // block entity there at all -- those render as nothing, since their
        // renderer has no originalState/pixelData to read.
        //
        // Fix: pick whichever voxel is actually occupied first, wherever it is.
        int masterIndex = -1;
        for (int i = 0; i < pixelData.length; i++) {
            if (pixelData[i] != 0) {
                masterIndex = i;
                break;
            }
        }

        if (masterIndex == -1) {
            // Nothing was sampled at all -- nothing to build.
            return;
        }

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
            // The corner of the cube: needed later to correctly convert
            // pixelData indices back into world positions (e.g. when this
            // master is destroyed and a new one has to be promoted), since the
            // master itself is not necessarily at that corner.
            masterBE.setStructureOrigin(origin);

            // Biome‑dependent grass colour – server‑side safe
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

        // ---- Slave blocks ----
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

    /**
     * Shared packing convention (also used by BlockPixelSampler and
     * MicroWorldBlockEntity): idx = (y * scale + z) * scale + x
     */
    private static BlockPos indexToPos(BlockPos origin, int index, int scale) {
        int x = index % scale;
        int rem = index / scale;
        int z = rem % scale;
        int y = rem / scale;
        return origin.offset(x, y, z);
    }
}