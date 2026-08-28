package com.archetipe.microworld.registry;

import com.archetipe.microworld.block.entity.MicroWorldBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(
                    Registries.BLOCK_ENTITY_TYPE,
                    "microworld"
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MicroWorldBlockEntity>> MICRO_WORLD_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("micro_world_block",
                    () -> BlockEntityType.Builder.of(
                            MicroWorldBlockEntity::new,
                            // Use direct registry lookup instead of DeferredHolder.get()
                            BuiltInRegistries.BLOCK.get(ResourceLocation.parse("microworld:micro_world_block"))
                    ).build(null)
            );
}