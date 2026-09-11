package com.archetipe.microworld.dimension;

import com.archetipe.microworld.Microworld;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class ModDimensions {

    private ModDimensions() {}

    public static final ResourceKey<Level> MICRO_WORLD_LEVEL = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(Microworld.MODID, "micro_world")
    );
}