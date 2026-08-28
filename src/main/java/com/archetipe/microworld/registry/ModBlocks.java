package com.archetipe.microworld.registry;

import com.archetipe.microworld.Microworld;
import net.minecraft.world.level.block.Block;
import com.archetipe.microworld.block.MicroWorldBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.level.material.MapColor;

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
}