package com.vardanrattan.echoes.events;

import com.vardanrattan.echoes.Echoes;
import com.vardanrattan.echoes.data.EchoWorldState;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Central place to register server-side event hooks.
 */
public final class EchoesServerEvents {

    private EchoesServerEvents() {
    }

    public static void register(RecordingSessionManager recordingSessionManager, PlaybackTriggerService playbackTriggerService) {
        if (recordingSessionManager == null) {
            throw new IllegalArgumentException("recordingSessionManager cannot be null");
        }
        if (playbackTriggerService == null) {
            throw new IllegalArgumentException("playbackTriggerService cannot be null");
        }

        // Capture rolling frames for players.
        ServerTickEvents.END_LEVEL_TICK.register(recordingSessionManager::onWorldTick);
        DiscoveryEchoHandler.register();
        JourneyEchoHandler.register();
        BossKillEchoHandler.register();

        // Finalize echo on player death.
        ServerLivingEntityEvents.AFTER_DEATH.register(recordingSessionManager::onDeath);

        // Trigger playback checks.
        ServerTickEvents.END_SERVER_TICK.register(playbackTriggerService::onServerTick);

        // Periodic decay per world.
        ServerTickEvents.END_LEVEL_TICK.register(world ->
                EchoWorldState.get(world).runDecayIfNeeded(System.currentTimeMillis()));

        // Cleanup to prevent buffers from accumulating.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            try {
                recordingSessionManager.clearBuffer(handler.player.getUUID());
                playbackTriggerService.clearPlayer(handler.player.getUUID());
                JourneyEchoHandler.clearPlayer(handler.player.getUUID());
            } catch (Exception e) {
                Echoes.LOGGER.debug("Failed to clear recording buffer on disconnect", e);
            }
        });
    }
}

