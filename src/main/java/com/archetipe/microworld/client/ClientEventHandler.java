package com.archetipe.microworld.client;

import com.archetipe.microworld.Microworld;
import com.archetipe.microworld.client.model.MicroWorldModelLoader;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber(modid = Microworld.MODID, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        ResourceLocation loaderId = ResourceLocation.parse(Microworld.MODID + ":micro_world_model");
        event.register(loaderId, new MicroWorldModelLoader());
    }

    @SubscribeEvent
    public static void onModelBakingCompleted(ModelEvent.BakingCompleted event) {
        VisualBaker.clearCache();
    }
}