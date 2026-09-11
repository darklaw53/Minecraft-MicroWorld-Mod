package com.archetipe.microworld;

import com.archetipe.microworld.dimension.MicroWorldFloodFillScheduler;
import com.archetipe.microworld.network.NetworkHelper;
import com.archetipe.microworld.registry.ModBlockEntities;
import com.archetipe.microworld.registry.ModBlocks;
import com.archetipe.microworld.registry.ModChunkGenerators;
import com.archetipe.microworld.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(Microworld.MODID)
public class Microworld {

    public static final String MODID = "microworld";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MICROWORLD_TAB =
            CREATIVE_MODE_TABS.register("microworld_tab",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.microworld"))
                            .withTabsBefore(CreativeModeTabs.COMBAT)
                            .icon(() -> ModItems.SHRINK_RAY.get().getDefaultInstance())
                            .displayItems((parameters, output) -> {
                                output.accept(ModItems.SHRINK_RAY.get());
                            })
                            .build()
            );

    public Microworld(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(NetworkHelper::register);   // <--- register network

        // Register deferred registers
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModChunkGenerators.CHUNK_GENERATORS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        // Register server events
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }

        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());
        Config.ITEM_STRINGS.get().forEach(item -> LOGGER.info("ITEM >> {}", item));
    }

    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");

        // MUST reset here: FRONTIER/VISITED in MicroWorldFloodFillScheduler
        // are static, so without this they silently carry over from one
        // Minecraft "world" into the next whenever both run inside the
        // same JVM session -- confirmed directly (a second world made
        // without fully restarting the client generated nothing but the
        // guaranteed floor block, because every coordinate the flood fill
        // tried was already marked VISITED from the first world). A new
        // IntegratedServer is created for every world load though, even
        // within one running client, so this fires every time it needs to.
        MicroWorldFloodFillScheduler.reset();
    }

    // NOTE: Two attempts at forcing a low view/simulation distance
    // programmatically (via ServerStartedEvent, then PlayerLoggedInEvent)
    // both failed -- vanilla re-applies the world's own configured distance
    // immediately after either hook runs, every time, regardless of timing.
    // This suggests the value is being pulled fresh from the world's stored
    // settings at multiple points during startup/login rather than being a
    // simple one-time-overwritable default. Set Simulation Distance directly
    // in the "More World Options" tab when creating a test world instead --
    // see conversation history for details.
}