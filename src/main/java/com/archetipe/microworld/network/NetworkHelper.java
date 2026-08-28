package com.archetipe.microworld.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NetworkHelper {

    private static final String PROTOCOL_VERSION = "1.0";

    private NetworkHelper() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("microworld")
                .versioned(PROTOCOL_VERSION)
                .optional();

        registrar.playToServer(
                MicroWorldDataPayload.TYPE,
                MicroWorldDataPayload.CODEC,
                MicroWorldDataPayload::handleOnServer
        );
    }

    /**
     * Called from the client to send the sampled data to the server.
     */
    public static void sendMicroWorldData(
            BlockPos origin,
            BlockState sourceState,
            int scale,
            short[] pixelData,
            BlockPos originalPos
    ) {
        var payload = new MicroWorldDataPayload(origin, sourceState, scale, pixelData, originalPos);
        PacketDistributor.sendToServer(payload);
    }
}