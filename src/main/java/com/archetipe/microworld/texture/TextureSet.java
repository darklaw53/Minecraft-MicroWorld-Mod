package com.archetipe.microworld.texture;

import net.minecraft.resources.ResourceLocation;

public record TextureSet(
        ResourceLocation top,
        ResourceLocation bottom,
        ResourceLocation north,
        ResourceLocation south,
        ResourceLocation east,
        ResourceLocation west
) {
}