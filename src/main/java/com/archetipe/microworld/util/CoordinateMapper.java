package com.archetipe.microworld.util;

import net.minecraft.core.BlockPos;

public class CoordinateMapper {

    /**
     * Every overworld block becomes a cube of this size in the MicroWorld.
     * 16 is ideal because vanilla block textures are 16x16 pixels.
     */
    public static final int SCALE = 16;

    /**
     * Converts a position in the MicroWorld into the corresponding
     * block position in the Overworld.
     */
    public static BlockPos toOverworld(BlockPos microPos) {
        return new BlockPos(
                Math.floorDiv(microPos.getX(), SCALE),
                Math.floorDiv(microPos.getY(), SCALE),
                Math.floorDiv(microPos.getZ(), SCALE)
        );
    }

    /**
     * Converts an Overworld block position into the origin
     * of its enlarged cube in the MicroWorld.
     */
    public static BlockPos toMicroOrigin(BlockPos overworldPos) {
        return new BlockPos(
                overworldPos.getX() * SCALE,
                overworldPos.getY() * SCALE,
                overworldPos.getZ() * SCALE
        );
    }

    /**
     * Returns the local coordinate inside the enlarged cube.
     * Values are always between 0 and SCALE - 1.
     */
    public static BlockPos getLocalPosition(BlockPos microPos) {
        return new BlockPos(
                Math.floorMod(microPos.getX(), SCALE),
                Math.floorMod(microPos.getY(), SCALE),
                Math.floorMod(microPos.getZ(), SCALE)
        );
    }

    private CoordinateMapper() {
    }
}