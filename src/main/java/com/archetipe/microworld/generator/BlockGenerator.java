package com.archetipe.microworld.generator;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Generates the enlarged version of a single Overworld block.
 */
public interface BlockGenerator {

    /**
     * Generates one block of the MicroWorld.
     *
     * @param world The chunk currently being generated.
     * @param microPos The MicroWorld position being generated.
     * @param sourceBlock The Overworld block this position belongs to.
     * @param localPos Position inside the enlarged block (0-15 in each axis).
     */
    void generate(
            WorldGenRegion world,
            BlockPos microPos,
            BlockState sourceBlock,
            BlockPos localPos
    );
}