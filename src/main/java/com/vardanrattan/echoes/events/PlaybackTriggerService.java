package com.vardanrattan.echoes.events;

import com.vardanrattan.echoes.config.EchoesConfig;
import com.vardanrattan.echoes.data.PlayerEchoData;
import com.vardanrattan.echoes.data.EchoRecord;
import com.vardanrattan.echoes.data.EchoWorldState;
import com.vardanrattan.echoes.network.EchoPlaybackPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side playback trigger logic.
 *
 * Every N ticks, checks for nearby unseen echoes and sends a playback payload.
 * Only one echo can be "active" per player at a time; the client must send
 * {@code EchoSeenPayload} to clear the active slot.
 */
public final class PlaybackTriggerService {

    private static final int DEFAULT_ACTIVE_TIMEOUT_TICKS = 20 * 30; // 30s failsafe

    private final Map<UUID, ActivePlayback> activeByPlayer = new HashMap<>();

    private long serverTick;

    public void onServerTick(MinecraftServer server) {
        if (server == null) return;
        serverTick++;

        EchoesConfig cfg = EchoesConfig.get();
        if (!cfg.isEnabled()) {
            return;
        }

        int interval = Math.max(1, cfg.getScanIntervalTicks());
        if (serverTick % interval != 0) {
            return;
        }

        int radius = Math.max(1, cfg.getTriggerRadius());

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player == null || player.isRemoved()) continue;

            // Respect display opt-out (player doesn't want to see echoes at all).
            ServerWorld world = (ServerWorld) player.getEntityWorld();
            EchoWorldState state = EchoWorldState.get(world);
            PlayerEchoData pd = state.getOrCreatePlayerData(player.getUuid());
            if (pd.isDisplayOptedOut()) {
                continue;
            }

            // Only one echo at a time per player; clear stale actives.
            ActivePlayback active = activeByPlayer.get(player.getUuid());
            if (active != null) {
                if ((serverTick - active.startedAtTick) > DEFAULT_ACTIVE_TIMEOUT_TICKS) {
                    activeByPlayer.remove(player.getUuid());
                } else {
                    continue;
                }
            }

            BlockPos center = player.getBlockPos();
            List<EchoRecord> candidates = state.getEchoesNear(center, radius, world.getRegistryKey());
            if (candidates.isEmpty()) continue;

            EchoRecord best = candidates.stream()
                    // Death echoes are replayable; other events are one-shot per player.
                    .filter(e -> e.getEventType() == com.vardanrattan.echoes.data.EchoEventType.DEATH || !e.hasBeenSeenBy(player.getUuid()))
                    .filter(e -> e.getEventType() == com.vardanrattan.echoes.data.EchoEventType.DEATH || !pd.hasSeenEcho(e.getUuid()))
                    .filter(e -> cfg.isSelfEchoesVisible() || (e.getPlayerUuid() == null || !e.getPlayerUuid().equals(player.getUuid())))
                    .min(Comparator.comparingDouble(e -> center.getSquaredDistance(e.getAnchorPos())))
                    .orElse(null);

            if (best == null) continue;

            // Send playback instruction.
            EchoPlaybackPayload payload = new EchoPlaybackPayload(
                    best.getUuid(),
                    best.getDimension(),
                    best.getAnchorPos(),
                    best.getTier(),
                    best.getEventType(),
                    best.getPlayerUuid() == null ? new UUID(0L, 0L) : best.getPlayerUuid(),
                    best.getPlayerName() == null ? "" : best.getPlayerName(),
                    best.getRealTimestamp(), // Added field
                    best.getEquipment(),
                    best.getFrames()
            );

            ServerPlayNetworking.send(player, payload);
            activeByPlayer.put(player.getUuid(), new ActivePlayback(best.getUuid(), serverTick));
        }
    }

    public void clearPlayer(UUID playerUuid) {
        if (playerUuid != null) {
            activeByPlayer.remove(playerUuid);
        }
    }

    public void onEchoSeen(UUID playerUuid, UUID echoUuid) {
        if (playerUuid == null || echoUuid == null) return;
        ActivePlayback active = activeByPlayer.get(playerUuid);
        if (active != null && echoUuid.equals(active.echoUuid)) {
            activeByPlayer.remove(playerUuid);
        }
    }

    private record ActivePlayback(UUID echoUuid, long startedAtTick) {
    }
}

