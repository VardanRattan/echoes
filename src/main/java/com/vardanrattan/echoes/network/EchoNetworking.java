package com.vardanrattan.echoes.network;

import com.vardanrattan.echoes.Echoes;
import com.vardanrattan.echoes.events.PlaybackTriggerService;
import com.vardanrattan.echoes.data.EchoWorldState;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.world.ServerWorld;

/**
 * Registers Echoes' custom payload types and (server-side) handlers.
 *
 * Uses the modern Fabric networking API:
 * - CustomPacketPayload records
 * - StreamCodec<RegistryByteBuf, T>
 * - PayloadTypeRegistry + ServerPlayNetworking
 */
public final class EchoNetworking {

    private EchoNetworking() {
    }

    /**
     * Called from common (server) initializer.
     */
    public static void init(PlaybackTriggerService playbackTriggerService) {
        registerPayloadTypes();
        registerServerReceivers(playbackTriggerService);
    }

    /**
     * Called from client initializer.
     */
    public static void initClient() {
        // Payload types are registered from the common initializer (`init`),
        // which runs on both dedicated servers and integrated clients.
        // Client-side receivers for EchoPlaybackPayload are registered in
        // EchoClientNetworking (step 4.2/4.3).
    }

    private static void registerPayloadTypes() {
        // Server → client playback
        PayloadTypeRegistry.playS2C().register(EchoPlaybackPayload.ID, EchoPlaybackPayload.CODEC);
        // Server → client sense (crystal right-click)
        PayloadTypeRegistry.playS2C().register(EchoSensePayload.ID, EchoSensePayload.CODEC);
        // Client → server seen-state reporting
        PayloadTypeRegistry.playC2S().register(EchoSeenPayload.ID, EchoSeenPayload.CODEC);
    }

    private static void registerServerReceivers(PlaybackTriggerService playbackTriggerService) {
        ServerPlayNetworking.registerGlobalReceiver(EchoSeenPayload.ID, (payload, context) -> {
            // Called on the server thread.
            var player = context.player();
            ServerWorld world = (ServerWorld) player.getEntityWorld();

            EchoWorldState state = EchoWorldState.get(world);
            boolean updated = state.markEchoSeen(payload.echoId(), player.getUuid());
            playbackTriggerService.onEchoSeen(player.getUuid(), payload.echoId());

            Echoes.LOGGER.debug("EchoSeenPayload echo={} player={} updated={}",
                    payload.echoId(),
                    player.getName().getString(),
                    updated);
        });
    }
}

