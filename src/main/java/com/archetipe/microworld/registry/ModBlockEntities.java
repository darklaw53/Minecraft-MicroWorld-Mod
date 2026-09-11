package com.archetipe.microworld.registry;

import com.archetipe.microworld.block.entity.MagnifiedBlockEntity;
import com.archetipe.microworld.block.entity.MicroWorldBlockEntity;
import com.archetipe.microworld.block.entity.MiniatureBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "microworld");

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MicroWorldBlockEntity>>
            MICRO_WORLD_BLOCK_ENTITY = BLOCK_ENTITIES.register("micro_world_block",
            () -> BlockEntityType.Builder.of(
                    MicroWorldBlockEntity::new,
                    BuiltInRegistries.BLOCK.get(ResourceLocation.parse("microworld:micro_world_block"))
            ).build(null)
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MagnifiedBlockEntity>>
            MAGNIFIED_BLOCK_ENTITY = BLOCK_ENTITIES.register("magnified_block",
            () -> BlockEntityType.Builder.of(
                    MagnifiedBlockEntity::new,
                    BuiltInRegistries.BLOCK.get(ResourceLocation.parse("microworld:magnified_block"))
            ).build(null)
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MiniatureBlockEntity>>
            MINIATURE_BLOCK_ENTITY = BLOCK_ENTITIES.register("miniature_block",
            () -> BlockEntityType.Builder.of(
                    MiniatureBlockEntity::new,
                    BuiltInRegistries.BLOCK.get(ResourceLocation.parse("microworld:miniature_block"))
            ).build(null)
    );
}