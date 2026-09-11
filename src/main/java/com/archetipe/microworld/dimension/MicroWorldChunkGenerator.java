package com.archetipe.microworld.dimension;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Deliberately produces an empty (all-air) chunk. Real terrain for the
 * MicroWorld dimension is NOT generated through the normal noise/carver/
 * surface pipeline -- it's populated reactively once a chunk finishes
 * loading (see MicroWorldChunkPopulator), because building it requires
 * reading fully-generated blocks from the overworld, which a ChunkGenerator
 * callback has no safe way to do mid-generation.
 */
public class MicroWorldChunkGenerator extends ChunkGenerator {

    public static final MapCodec<MicroWorldChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
            ).apply(instance, MicroWorldChunkGenerator::new)
    );

    public MicroWorldChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState randomState,
                             BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk,
                             GenerationStep.Carving carving) {
        // no-op: nothing to carve in an empty chunk
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager,
                             RandomState randomState, ChunkAccess chunk) {
        // no-op: MicroWorldChunkPopulator fills real terrain after load
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {
        // no natural spawns during generation
    }

    @Override
    public int getGenDepth() {
        // Matches "height" in data/microworld/dimension_type/micro_world.json.
        return 3056;
    }

    @Override
    public int getSeaLevel() {
        // No natural terrain/water generation happens through this
        // generator at all (see class comment) -- this value isn't actually
        // used for anything meaningful here, just required by the abstract
        // class. Picking 0 rather than trying to scale the overworld's sea
        // level (63), since there's no real "sea" in this dimension to place
        // relative to.
        return 0;
    }

    @Override
    public int getMinY() {
        // Must match "min_y" in data/microworld/dimension_type/micro_world.json.
        // A mismatch here can cause broken height/chunk-section math even
        // though it won't necessarily fail to compile or crash immediately.
        return -1024;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender,
                                                        RandomState randomState,
                                                        StructureManager structureManager,
                                                        ChunkAccess chunk) {
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        return level.getMinBuildHeight();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        return new NoiseColumn(level.getMinBuildHeight(), new BlockState[0]);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        // no-op
    }
}