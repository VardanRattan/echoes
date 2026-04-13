package com.vardanrattan.echoes.events;

import com.vardanrattan.echoes.config.EchoesConfig;
import com.vardanrattan.echoes.data.EquipmentSnapshot;
import com.vardanrattan.echoes.data.EchoEventType;
import com.vardanrattan.echoes.data.EchoFrame;
import com.vardanrattan.echoes.data.EchoRecord;
import com.vardanrattan.echoes.data.EchoWorldState;
import com.vardanrattan.echoes.Echoes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Tracks per-player frame buffers and finalizes recordings on death.
 *
 * Uses a pre-recording approach: frames are captured every 2 ticks into a
 * rolling buffer. When a death occurs, the last N frames are converted to an
 * EchoRecord and saved.
 *
 * D1: When asyncRecording = true in config, frame capture is dispatched to a
 *     common ForkJoinPool via CompletableFuture.runAsync(). The buffer is
 *     switched to ConcurrentLinkedDeque so the async writer and main-thread
 *     reader don't race.
 *
 * D2: lastSafePos tracks the most recent position where the player was on
 *     solid ground or above void threshold. Death echoes are anchored here
 *     rather than at the raw death position, so void/lava deaths don't place
 *     ghosts in mid-air or inside lava.
 */
public final class RecordingSessionManager {

    private static final int FRAME_CAPTURE_INTERVAL_TICKS = 2;
    private static final int DEATH_FRAME_COUNT = 50; // 5 seconds at 10 fps
    private static final int MAX_BUFFER_FRAMES = 100; // 10 seconds at 10 fps
    private static final int MANUAL_MAX_TICKS = 160; // 8 seconds at 20 tps

    // D2: Vertical threshold below which a position is considered unsafe.
    // Using bottomY + 10 means "close to void or void-adjacent" = unsafe.
    private static final int SAFE_Y_MARGIN = 10;

    // Sync buffer (ArrayDeque) used when async-recording = false.
    private final Map<UUID, Deque<BufferedFrame>> playerBuffers = new HashMap<>();
    private final Map<UUID, Integer> worldTickCounters = new HashMap<>();

    // D2: Last position where the player was on solid ground or above void margin.
    // Transient (not persisted) — resets on restart, which is fine.
    private final Map<UUID, BlockPos> lastSafePos = new HashMap<>();

    // F1: Manual recording sessions triggered by Echo Crystal.
    private record ManualRecordSession(BlockPos anchor, long startTick) {}
    private final Map<UUID, ManualRecordSession> manualSessions = new HashMap<>();

    /**
     * Called every world tick. Captures a frame for each player every 2 ticks.
     *
     * D1: If asyncRecording is enabled, frame capture is run off the main thread
     *     using CompletableFuture.runAsync(). The buffer type is promoted to
     *     ConcurrentLinkedDeque when async mode is active.
     */
    public void onWorldTick(ServerLevel world) {
        if (!EchoesConfig.get().isEnabled() || !EchoesConfig.get().isDeathEnabled()) {
            return;
        }

        boolean async = EchoesConfig.get().isAsyncRecording();
        int worldBottomY = world.getMinY();
        long serverTick = world.getServer().getTickCount();

        world.players().forEach(player -> {
            if (player == null || player.isRemoved()) {
                return;
            }
            UUID uuid = player.getUUID();

            // D2: Update last safe position on the main thread (always, regardless of async).
            // "Safe" = on ground, or Y is well above the void floor.
            if (player.onGround() || player.getY() > worldBottomY + SAFE_Y_MARGIN) {
                lastSafePos.put(uuid, player.blockPosition().immutable());
            }

            Integer currentTick = worldTickCounters.get(uuid);
            int nextTick = (currentTick == null ? 0 : currentTick) + 1;
            worldTickCounters.put(uuid, nextTick);

            // F1: Check if manual recording is finished.
            ManualRecordSession manual = manualSessions.get(uuid);
            if (manual != null && (serverTick - manual.startTick()) >= MANUAL_MAX_TICKS) {
                finalizeManualRecording(player, manual);
                manualSessions.remove(uuid);
            }

            if (nextTick % FRAME_CAPTURE_INTERVAL_TICKS != 0) {
                return;
            }

            if (async) {
                // D1: Promote buffer to ConcurrentLinkedDeque on first async use.
                playerBuffers.computeIfAbsent(uuid, k -> new ConcurrentLinkedDeque<>());
                // Capture player state snapshot on main thread before handing off.
                // ServerPlayerEntity position/rotation are read here while still
                // on the server thread; only the buffer write happens async.
                final float x = (float) player.getX();
                final float y = (float) player.getY();
                final float z = (float) player.getZ();
                final float yaw = player.getYRot();
                final float pitch = player.getXRot();

                CompletableFuture.runAsync(() -> {
                    BufferedFrame frame = FrameSampler.captureFromSnapshot(x, y, z, yaw, pitch, player);
                    if (frame != null) {
                        Deque<BufferedFrame> buffer = playerBuffers.get(uuid);
                        if (buffer instanceof ConcurrentLinkedDeque<BufferedFrame>) {
                            // ConcurrentLinkedDeque has no size() in O(1); cap with
                            // a volatile counter by trimming on offer.
                            buffer.addLast(frame);
                            // Trim to max. CLDeque.size() is O(n) but only runs
                            // every 2 ticks per player — acceptable cost.
                            if (buffer.size() > MAX_BUFFER_FRAMES) {
                                buffer.removeFirst();
                            }
                        }
                    }
                });
            } else {
                // Sync path: plain ArrayDeque, main thread only.
                BufferedFrame frame = FrameSampler.captureBufferedFrame(player);
                if (frame != null) {
                    Deque<BufferedFrame> buffer = playerBuffers.computeIfAbsent(uuid, k -> new ArrayDeque<>());
                    buffer.addLast(frame);
                    while (buffer.size() > MAX_BUFFER_FRAMES) {
                        buffer.removeFirst();
                    }
                }
            }
        });
    }

    /**
     * Called when a living entity dies. Finalizes the recording for players.
     *
     * D2: Uses lastSafePos as the echo anchor instead of the raw death position,
     *     so void/lava deaths place the ghost at the last safe solid position.
     */
    public void onDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        // If player was manually recording, cancel it to avoid double-echoes or corrupt state.
        manualSessions.remove(player.getUUID());

        if (!EchoesConfig.get().isEnabled() || !EchoesConfig.get().isDeathEnabled()) {
            return;
        }

        ServerLevel serverWorld = player.level();

        Deque<BufferedFrame> buffer = playerBuffers.remove(player.getUUID());
        worldTickCounters.remove(player.getUUID());

        if (buffer == null || buffer.isEmpty()) {
            return;
        }

        // Respect opt-out
        EchoWorldState worldState = EchoWorldState.get(serverWorld);
        var playerData = worldState.getOrCreatePlayerData(player.getUUID());
        if (playerData.isOptedOut()) {
            return;
        }

        // D2: Prefer the last safe position over the raw death position.
        // This prevents mid-void / mid-lava ghost anchoring.
        BlockPos anchorPos = lastSafePos.getOrDefault(player.getUUID(), player.blockPosition()).immutable();
        lastSafePos.remove(player.getUUID());

        int frameCount = Math.min(buffer.size(), DEATH_FRAME_COUNT);

        // Take the last N frames (oldest first for correct tick ordering)
        List<BufferedFrame> framesToUse = new ArrayList<>(frameCount);
        int skip = buffer.size() - frameCount;
        int i = 0;
        for (BufferedFrame f : buffer) {
            if (i++ < skip)
                continue;
            framesToUse.add(f);
        }
        if (framesToUse.isEmpty()) {
            Echoes.LOGGER.debug(
                    "RecordingSessionManager.onDeath: no usable frames after trimming buffer for player={} bufferSize={}",
                    player.getUUID(), buffer.size());
            return;
        }

        List<EchoFrame> echoFrames = new ArrayList<>(framesToUse.size());
        for (int j = 0; j < framesToUse.size(); j++) {
            BufferedFrame bf = framesToUse.get(j);
            EchoFrame frame = FrameSampler.toRelativeEchoFrame(bf, anchorPos, j * FRAME_CAPTURE_INTERVAL_TICKS);
            if (frame != null) {
                echoFrames.add(frame);
            }
        }
        if (echoFrames.isEmpty()) {
            return;
        }

        EquipmentSnapshot equipment = EquipmentSnapshot.capture(player);

        EchoRecord record = EchoService.createEchoFromFrames(
                serverWorld,
                player,
                EchoEventType.DEATH,
                anchorPos,
                echoFrames,
                equipment);

        worldState.addEcho(record);
        EchoService.onEchoCreated(record);

        Echoes.LOGGER.debug("Created DEATH echo {} for {} at {} in {} (frames={}, safeAnchor={})",
                record.getUUID(),
                player.getName().getString(),
                anchorPos,
                serverWorld.dimension().identifier(),
                record.getFrameCount(),
                !anchorPos.equals(player.blockPosition()));
    }

    /**
     * Clears the buffer for a player (e.g. on disconnect).
     */
    public void clearBuffer(UUID playerUuid) {
        if (playerUuid != null) {
            playerBuffers.remove(playerUuid);
            worldTickCounters.remove(playerUuid);
            lastSafePos.remove(playerUuid);
            manualSessions.remove(playerUuid);
        }
    }

    /**
     * F1: Starts a manual recording session for a player.
     * Captured frames will be pinned to the current position.
     */
    public void startManualRecording(ServerPlayer player) {
        ServerLevel level = player.level();
        var worldState = EchoWorldState.get(level);
        var playerData = worldState.getOrCreatePlayerData(player.getUUID());
        if (playerData.isOptedOut()) {
            return;
        }

        long tick = level.getServer().getTickCount();
        manualSessions.put(player.getUUID(),
                new ManualRecordSession(player.blockPosition().immutable(), tick));
    }

    private void finalizeManualRecording(ServerPlayer player, ManualRecordSession session) {
        Deque<BufferedFrame> buffer = playerBuffers.get(player.getUUID());
        if (buffer == null || buffer.isEmpty()) return;

        // Manual recordings use the same 8s/160t logic as SCAR echoes.
        // We take the last 80 frames (captured every 2 ticks = 160 ticks).
        int manualFrameCount = 80;
        int frameCount = Math.min(buffer.size(), manualFrameCount);

        List<BufferedFrame> framesToUse = new ArrayList<>(frameCount);
        int skip = buffer.size() - frameCount;
        int i = 0;
        for (BufferedFrame f : buffer) {
            if (i++ < skip) continue;
            framesToUse.add(f);
        }

        List<EchoFrame> echoFrames = new ArrayList<>(framesToUse.size());
        for (int j = 0; j < framesToUse.size(); j++) {
            BufferedFrame bf = framesToUse.get(j);
            EchoFrame frame = FrameSampler.toRelativeEchoFrame(bf, session.anchor(), j * FRAME_CAPTURE_INTERVAL_TICKS);
            if (frame != null) {
                echoFrames.add(frame);
            }
        }

        if (echoFrames.isEmpty()) return;

        ServerLevel world = player.level();
        EchoWorldState state = EchoWorldState.get(world);
        var equipment = EquipmentSnapshot.capture(player);

        var record = EchoService.createEchoFromFrames(
                world,
                player,
                EchoEventType.MANUAL_CRYSTAL,
                session.anchor(),
                echoFrames,
                equipment
        );

        state.addEcho(record);
        EchoService.onEchoCreated(record);
        state.setDirty();

        Echoes.LOGGER.info("Manual echo created for player={} at {}", player.getName().getString(), session.anchor());
    }
}
