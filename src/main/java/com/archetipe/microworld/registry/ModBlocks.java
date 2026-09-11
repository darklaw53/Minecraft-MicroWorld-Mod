package com.archetipe.microworld.registry;

import com.archetipe.microworld.block.MicroWorldBlock;
import com.archetipe.microworld.block.MiniatureBlock;
import com.archetipe.microworld.block.SolidAirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks("microworld");

    public static final DeferredBlock<Block> MICRO_WORLD_BLOCK =
            BLOCKS.register("micro_world_block",
                    () -> new MicroWorldBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .strength(1.0f)
                            .noOcclusion()
                    )
            );

    public static final DeferredBlock<Block> SOLID_AIR =
            BLOCKS.register("solid_air",
                    () -> new SolidAirBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.NONE)
                            .strength(1.0f)
                            .noOcclusion()
                            .noLootTable()
                    )
            );

    public static final DeferredBlock<Block> MINIATURE_BLOCK =
            BLOCKS.register("miniature_block",
                    () -> new MiniatureBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .strength(0.5f)
                            .noOcclusion()
                            .dynamicShape()
                            .noLootTable()
                    )
            );
}