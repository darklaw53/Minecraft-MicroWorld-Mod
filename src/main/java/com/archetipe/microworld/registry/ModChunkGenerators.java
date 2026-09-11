package com.archetipe.microworld.registry;

import com.archetipe.microworld.Microworld;
import com.archetipe.microworld.dimension.MicroWorldChunkGenerator;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModChunkGenerators {

    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(BuiltInRegistries.CHUNK_GENERATOR, Microworld.MODID);

    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<MicroWorldChunkGenerator>> VOID_GENERATOR =
            CHUNK_GENERATORS.register("void_generator", () -> MicroWorldChunkGenerator.CODEC);
}