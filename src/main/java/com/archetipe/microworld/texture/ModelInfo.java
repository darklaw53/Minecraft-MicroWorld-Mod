package com.archetipe.microworld.texture;

import net.minecraft.resources.ResourceLocation;

public record ModelInfo(
        ResourceLocation top,
        ResourceLocation bottom,
        ResourceLocation north,
        ResourceLocation south,
        ResourceLocation east,
        ResourceLocation west
) {

    public ResourceLocation get(Face face) {
        return switch (face) {
            case TOP -> top;
            case BOTTOM -> bottom;
            case NORTH -> north;
            case SOUTH -> south;
            case EAST -> east;
            case WEST -> west;
        };
    }

}