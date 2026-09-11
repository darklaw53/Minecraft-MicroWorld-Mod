package com.archetipe.microworld.client;

import com.archetipe.microworld.Microworld;
import com.archetipe.microworld.client.model.MicroWorldModelLoader;
import com.archetipe.microworld.client.model.MiniatureModelLoader;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber(modid = Microworld.MODID, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(
                ResourceLocation.parse(Microworld.MODID + ":micro_world_model"),
                new MicroWorldModelLoader()
        );
        event.register(
                ResourceLocation.parse(Microworld.MODID + ":miniature_model"),
                new MiniatureModelLoader()
        );
    }

    @SubscribeEvent
    public static void onModelBakingCompleted(ModelEvent.BakingCompleted event) {
        VisualBaker.clearCache();
    }
}