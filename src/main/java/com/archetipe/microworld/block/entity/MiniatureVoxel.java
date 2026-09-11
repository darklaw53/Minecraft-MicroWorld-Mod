package com.archetipe.microworld.block.entity;

import net.minecraft.world.level.block.state.BlockState;

public record MiniatureVoxel(BlockState state, short pixel, boolean sampled) {

    public static MiniatureVoxel sampled(BlockState source, short pixel) {
        return new MiniatureVoxel(source, pixel, true);
    }

    public static MiniatureVoxel real(BlockState state) {
        return new MiniatureVoxel(state, (short) 0, false);
    }
}