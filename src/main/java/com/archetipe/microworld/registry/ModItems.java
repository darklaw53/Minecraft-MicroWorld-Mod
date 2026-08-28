package com.archetipe.microworld.registry;

import com.archetipe.microworld.Microworld;
import com.archetipe.microworld.item.ShrinkRayItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(Microworld.MODID);

    public static final DeferredItem<Item> SHRINK_RAY =
            ITEMS.register(
                    "shrink_ray",
                    () -> new ShrinkRayItem(
                            new Item.Properties()
                                    .stacksTo(1)
                    )
            );

}