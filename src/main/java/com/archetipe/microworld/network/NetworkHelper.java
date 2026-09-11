package com.archetipe.microworld.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NetworkHelper {

    private NetworkHelper() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("microworld");

        registrar.playToServer(
                MicroWorldDataPayload.TYPE,
                MicroWorldDataPayload.CODEC,
                MicroWorldDataPayload::handleOnServer
        );

        registrar.playToServer(
                PlaceMiniaturePayload.TYPE,
                PlaceMiniaturePayload.CODEC,
                PlaceMiniaturePayload::handleOnServer
        );
    }

    public static void sendMicroWorldData(
            BlockPos origin,
            BlockState sourceState,
            int scale,
            short[] pixelData,
            BlockPos originalPos
    ) {
        PacketDistributor.sendToServer(
                new MicroWorldDataPayload(origin, sourceState, scale, pixelData, originalPos));
    }

    public static void sendPlaceMiniature(BlockPos placePos) {
        PacketDistributor.sendToServer(new PlaceMiniaturePayload(placePos));
    }
}