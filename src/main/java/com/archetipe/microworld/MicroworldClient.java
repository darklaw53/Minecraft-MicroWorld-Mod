package com.archetipe.microworld;

import com.archetipe.microworld.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = Microworld.MODID, value = Dist.CLIENT)
public class MicroworldClient {

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        Microworld.LOGGER.info("HELLO FROM CLIENT SETUP");
        Microworld.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());

        // Set render layer to cutout for transparency
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.MICRO_WORLD_BLOCK.get(), RenderType.cutout());
        });
    }
}