package com.vardanrattan.echoes.events;

import com.vardanrattan.echoes.config.EchoesConfig;
import com.vardanrattan.echoes.data.EchoEventType;
import com.vardanrattan.echoes.data.EchoFrame;
import com.vardanrattan.echoes.data.EchoWorldState;
import com.vardanrattan.echoes.data.EquipmentSnapshot;
import com.vardanrattan.echoes.data.PlayerEchoData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles journey tracking (D3) and journey triggers (E5).
 * Detects teleports to reset session tracking and fires echoes at distance milestones.
 */
public final class JourneyEchoHandler {

    private static final int SAMPLE_INTERVAL_TICKS = 20; // 1 second
    private static final double TELEPORT_THRESHOLD_SQ = 100.0; // 10 blocks jump

    private static final Map<UUID, Vec3d> lastTickPositions = new HashMap<>();

    private JourneyEchoHandler() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(JourneyEchoHandler::onWorldTick);
    }

    private static void onWorldTick(ServerWorld world) {
        EchoesConfig cfg = EchoesConfig.get();
        if (!cfg.isEnabled()) {
            return;
        }

        long time = world.getTime();
        boolean isSampleTick = time % SAMPLE_INTERVAL_TICKS == 0;

        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player == null) continue;
            
            if (player.isRemoved()) {
                lastTickPositions.remove(player.getUuid());
                continue;
            }

            UUID uuid = player.getUuid();
            Vec3d currentPos = new Vec3d(player.getX(), player.getY(), player.getZ());
            Vec3d lastPos = lastTickPositions.get(uuid);

            EchoWorldState state = EchoWorldState.get(world);
            PlayerEchoData data = state.getOrCreatePlayerData(uuid);

            if (lastPos != null) {
                // D3: Teleport detection
                double distSq = currentPos.squaredDistanceTo(lastPos);
                if (distSq > TELEPORT_THRESHOLD_SQ) {
                    // Teleport detected, reset journey
                    data.setSessionOrigin(player.getBlockPos().toImmutable());
                    data.resetSessionDistance();
                    state.markDirty();
                } else if (isSampleTick) {
                    // Accumulate distance (using actual BlockPos delta or Vec3d delta)
                    // We use the sample interval to avoid floating point noise every tick.
                    float delta = (float) currentPos.distanceTo(lastPos);
                    data.addToSessionDistanceTraveled(delta);
                    state.markDirty();

                    // E5: Journey triggers
                    handleJourneyMilestones(world, player, state, data, cfg);
                }
            } else {
                // Initialize session if not present
                if (data.getSessionOrigin() == null) {
                    data.setSessionOrigin(player.getBlockPos().toImmutable());
                    state.markDirty();
                }
            }

            lastTickPositions.put(uuid, currentPos);
        }
    }

    private static void handleJourneyMilestones(ServerWorld world, ServerPlayerEntity player, EchoWorldState state, PlayerEchoData data, EchoesConfig cfg) {
        float dist = data.getSessionDistanceTraveled();
        
        // JOURNEY_LONG (Tier 1) - 500 blocks
        if (dist >= cfg.getJourneyTier1Distance() && !data.getCraftedMilestones().contains("journey:tier1")) {
            data.addCraftedMilestone("journey:tier1");
            emitJourneyEcho(world, player, state, EchoEventType.JOURNEY_LONG);
        }
        
        // JOURNEY_MARATHON (Tier 3) - 2000 blocks
        if (dist >= cfg.getJourneyTier2Distance() && !data.getCraftedMilestones().contains("journey:tier2")) {
            data.addCraftedMilestone("journey:tier2");
            emitJourneyEcho(world, player, state, EchoEventType.JOURNEY_MARATHON);
        }
    }

    private static void emitJourneyEcho(ServerWorld world, ServerPlayerEntity player, EchoWorldState state, EchoEventType type) {
        List<EchoFrame> frames = FrameSampler.singleFrame(world, player);
        if (!frames.isEmpty()) {
            var equipment = EquipmentSnapshot.capture(player);
            var record = EchoService.createEchoFromFrames(
                    world,
                    player,
                    type,
                    player.getBlockPos(),
                    frames,
                    equipment
            );
            state.addEcho(record);
            EchoService.onEchoCreated(record);
            state.markDirty();
        }
    }

    public static void clearPlayer(UUID uuid) {
        lastTickPositions.remove(uuid);
    }
}
