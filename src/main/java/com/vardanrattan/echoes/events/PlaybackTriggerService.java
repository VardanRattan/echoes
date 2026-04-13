package com.vardanrattan.echoes.events;

import com.vardanrattan.echoes.config.EchoesConfig;
import com.vardanrattan.echoes.data.PlayerEchoData;
import com.vardanrattan.echoes.data.EchoRecord;
import com.vardanrattan.echoes.data.EchoWorldState;
import com.vardanrattan.echoes.network.EchoPlaybackPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

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

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == null || player.isRemoved()) continue;

            // Respect display opt-out (player doesn't want to see echoes at all).
            ServerLevel world = player.level();
            EchoWorldState state = EchoWorldState.get(world);
            PlayerEchoData pd = state.getOrCreatePlayerData(player.getUUID());
            if (pd.isDisplayOptedOut()) {
                continue;
            }

            // Only one echo at a time per player; clear stale actives.
            ActivePlayback active = activeByPlayer.get(player.getUUID());
            if (active != null) {
                if ((serverTick - active.startedAtTick) > DEFAULT_ACTIVE_TIMEOUT_TICKS) {
                    activeByPlayer.remove(player.getUUID());
                } else {
                    continue;
                }
            }

            BlockPos center = player.blockPosition();
            List<EchoRecord> candidates = state.getEchoesNear(center, radius, world.dimension());
            if (candidates.isEmpty()) continue;

            EchoRecord best = candidates.stream()
                    // Death echoes are replayable; other events are one-shot per player.
                    .filter(e -> e.getEventType() == com.vardanrattan.echoes.data.EchoEventType.DEATH || !e.hasBeenSeenBy(player.getUUID()))
                    .filter(e -> e.getEventType() == com.vardanrattan.echoes.data.EchoEventType.DEATH || !pd.hasSeenEcho(e.getUUID()))
                    .filter(e -> cfg.isSelfEchoesVisible() || (e.getPlayerUuid() == null || !e.getPlayerUuid().equals(player.getUUID())))
                    .min(Comparator.comparingDouble(e -> center.distSqr(e.getAnchorPos())))
                    .orElse(null);

            if (best == null) continue;

            // Send playback instruction.
            UUID resolvedUuid = com.vardanrattan.echoes.network.EchoPrivacy.resolvePlayerUuid(best);
            String resolvedName = com.vardanrattan.echoes.network.EchoPrivacy.resolvePlayerName(best);

            EchoPlaybackPayload payload = new EchoPlaybackPayload(
                    best.getUUID(),
                    best.getDimension(),
                    best.getAnchorPos(),
                    best.getTier(),
                    best.getEventType(),
                    resolvedUuid == null ? new UUID(0L, 0L) : resolvedUuid,
                    resolvedName == null ? "" : resolvedName,
                    best.getRealTimestamp(),
                    best.getEquipment(),
                    best.getFrames()
            );

            ServerPlayNetworking.send(player, payload);
            activeByPlayer.put(player.getUUID(), new ActivePlayback(best.getUUID(), serverTick));
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

