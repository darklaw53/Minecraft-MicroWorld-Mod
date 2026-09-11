package com.archetipe.microworld.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Everything the server needs to remember between click 1 and click 2. */
public record PendingMegablock(
        BlockPos origin,
        BlockState source,
        int scale,
        short[] pixelData,
        BlockPos originalPos
) {}