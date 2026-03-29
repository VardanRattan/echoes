package com.vardanrattan.echoes.events;

import com.vardanrattan.echoes.data.EchoEventType;
import com.vardanrattan.echoes.data.EchoFrame;
import com.vardanrattan.echoes.data.EchoRecord;
import com.vardanrattan.echoes.data.EchoTier;
import com.vardanrattan.echoes.data.EquipmentSnapshot;
import com.vardanrattan.echoes.Echoes;
import com.vardanrattan.echoes.data.EchoWorldState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Central echo factory and taxonomy logic.
 *
 * All event handlers should funnel through this service so that:
 * - Tier assignment lives in one place.
 * - Recording durations / frame limits can be tuned in one place.
 */
public final class EchoService {

    // Simple in-memory counters for tuning echo frequency.
    // H4: These are session-only and reset on server restart.
    private static long totalEchoes;
    private static long whisperEchoes;
    private static long markEchoes;
    private static long scarEchoes;

    private EchoService() {
    }

    /**
     * Creates an EchoRecord from already-captured frames.
     * Does not write it to world state; callers should pass it to EchoWorldState.addEcho.
     */
    public static EchoRecord createEchoFromFrames(
            ServerWorld world,
            ServerPlayerEntity player,
            EchoEventType eventType,
            BlockPos anchorPos,
            List<EchoFrame> frames,
            EquipmentSnapshot equipment
    ) {
        EchoTier tier = tierFor(eventType);
        
        // E6: World first check
        if (isWorldFirstEligible(eventType, world)) {
            String worldFirstKey = "world_first:" + eventType.name();
            if (EchoWorldState.get(world).claimWorldFirst(worldFirstKey)) {
                tier = EchoTier.WORLD_FIRST;
            }
        }

        UUID playerUuid = player.getUuid();
        String playerName = player.getName().getString();

        return new EchoRecord(
                UUID.randomUUID(),
                playerUuid,
                playerName,
                eventType,
                tier,
                world.getRegistryKey(),
                anchorPos,
                world.getTime(),
                System.currentTimeMillis(),
                equipment,
                frames,
                new HashSet<>()
        );
    }

    private static boolean isWorldFirstEligible(EchoEventType type, ServerWorld world) {
        if (type == EchoEventType.BOSS_KILL) return true;
        if (type == EchoEventType.DIMENSION_ENTER && world.getRegistryKey() == World.END) return true;
        return false;
    }

    /**
     * Returns default tier for a given event type using the design doc taxonomy.
     * More context-specific variations (catastrophic deaths, world-firsts, etc.)
     * will be layered on later.
     */
    public static EchoTier tierFor(EchoEventType type) {
        return switch (type) {
            // Tier 1 — Whispers
            case BIOME_DISCOVERY, JOURNEY_LONG, DIMENSION_ENTER -> EchoTier.WHISPER;

            // Tier 2 — Marks
            case DEATH, STRUCTURE_DISCOVERY, MAJOR_CRAFT, TAMING -> EchoTier.MARK;

            // Tier 3 — Scars / special
            case BOSS_KILL, JOURNEY_MARATHON, WORLD_FIRST, MANUAL_CRYSTAL -> EchoTier.SCAR;
        };
    }

    /**
     * Default maximum recording length in ticks for an event type.
     * This is mostly for future non-prebuffered recordings.
     */
    public static int maxRecordingTicks(EchoEventType type) {
        return switch (type) {
            case DEATH, STRUCTURE_DISCOVERY, DIMENSION_ENTER, MAJOR_CRAFT, TAMING -> 20 * 5; // ~5s
            case BIOME_DISCOVERY, JOURNEY_LONG, MANUAL_CRYSTAL -> 20 * 3; // ~3s
            case BOSS_KILL, JOURNEY_MARATHON, WORLD_FIRST -> 20 * 8; // ~8s
        };
    }

    /**
     * Called whenever a new echo is added to world state.
     * Currently just tracks simple counters and logs at low frequency for tuning.
     */
    public static void onEchoCreated(EchoRecord record) {
        if (record == null) return;
        totalEchoes++;
        switch (record.getTier()) {
            case WHISPER -> whisperEchoes++;
            case MARK -> markEchoes++;
            case SCAR, WORLD_FIRST -> scarEchoes++;
        }

        // Log every 50 echoes as a lightweight tuning hook.
        if (totalEchoes % 50 == 0) {
            Echoes.LOGGER.info("Echo metrics: total={} whisper={} mark={} scar+worldFirst={}",
                    totalEchoes, whisperEchoes, markEchoes, scarEchoes);
        }
    }
}

