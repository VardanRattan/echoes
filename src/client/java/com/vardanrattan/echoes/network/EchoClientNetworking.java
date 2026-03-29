package com.vardanrattan.echoes.network;

import com.vardanrattan.echoes.Echoes;
import com.vardanrattan.echoes.data.EchoTier;
import com.vardanrattan.echoes.data.EquipmentSnapshot;
import com.vardanrattan.echoes.entity.GhostPlayerEntity;
import com.vardanrattan.echoes.render.GhostPlayerRenderer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Client-side networking handlers.
 *
 * Architecture (A1 + A2):
 * - ghost.tick() (state advance) runs on END_CLIENT_TICK.
 * - All draw calls run on WorldRenderEvents.AFTER_ENTITIES so we
 *   have access to MatrixStack, VertexConsumerProvider, and sub-tick
 *   tickDelta for smooth interpolation.
 * - Audio (C1) is also triggered here on echo start.
 */
public final class EchoClientNetworking {

    private static final Map<UUID, Integer> pendingSeenTicks = new HashMap<>();
    private static final Map<UUID, ActiveGhost> activeGhosts = new HashMap<>();
    private static final Map<UUID, EchoSensePayload.SenseEntry> nearbyEchoesCache = new HashMap<>();

    private record ActiveGhost(
            GhostPlayerEntity ghost,
            EchoTier tier,
            com.vardanrattan.echoes.data.EchoEventType eventType,
            BlockPos anchor,
            EquipmentSnapshot equipment,
            UUID playerUuid,
            String playerName,
            long startTimeMs) {
    }

    private EchoClientNetworking() {
    }

    public static void init() {
        // -----------------------------------------------------------------------
        // Network receive handler
        // -----------------------------------------------------------------------
        ClientPlayNetworking.registerGlobalReceiver(EchoPlaybackPayload.ID, (payload, context) -> {
            int durationTicks = estimateDurationTicks(payload);
            pendingSeenTicks.put(payload.echoId(), durationTicks);

            Echoes.LOGGER.debug("Received EchoPlaybackPayload echo={} frames={} tier={} event={}",
                    payload.echoId(), payload.frames().size(), payload.tier(), payload.eventType());

            if (payload.frames() != null && !payload.frames().isEmpty()) {
                GhostPlayerEntity ghost = new GhostPlayerEntity(
                        payload.frames(),
                        20, // fade-in ticks (~1s)
                        30 // fade-out ticks (~1.5s)
                );
                activeGhosts.put(payload.echoId(), new ActiveGhost(
                        ghost,
                        payload.tier(),
                        payload.eventType(),
                        payload.anchorPos(),
                        payload.equipment(),
                        payload.playerUuid(),
                        payload.playerName(),
                        payload.realTimestamp()));

                // C1 — play tier/event appropriate vanilla sound on echo start.
                playEchoStartSound(payload, context.client());
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(EchoSensePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                ClientWorld world = context.client().world;
                if (world == null) return;

                nearbyEchoesCache.clear();
                for (EchoSensePayload.SenseEntry entry : payload.nearbyEchoes()) {
                    nearbyEchoesCache.put(entry.echoId(), entry);
                    BlockPos pos = entry.pos();
                    // Local particle burst at each echo anchor.
                    for (int i = 0; i < 10; i++) {
                        context.client().particleManager.addParticle(
                                ParticleTypes.SOUL,
                                (double) pos.getX() + 0.5 + (world.random.nextDouble() - 0.5) * 0.8,
                                (double) pos.getY() + 1.0 + (world.random.nextDouble() - 0.5) * 0.8,
                                (double) pos.getZ() + 0.5 + (world.random.nextDouble() - 0.5) * 0.8,
                                0.0, 0.02, 0.0
                        );
                    }
                }
            });
        });

        // -----------------------------------------------------------------------
        // A1: END_CLIENT_TICK — state advance only (no draw calls here).
        // -----------------------------------------------------------------------
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientWorld world = client.world;
            if (world == null) {
                pendingSeenTicks.clear();
                activeGhosts.clear();
                return;
            }

            // Advance seen-state timers and send EchoSeenPayload when expired.
            if (!pendingSeenTicks.isEmpty()) {
                Iterator<Map.Entry<UUID, Integer>> it = pendingSeenTicks.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<UUID, Integer> e = it.next();
                    int remaining = e.getValue() - 1;
                    if (remaining > 0) {
                        e.setValue(remaining);
                        continue;
                    }
                    UUID echoId = e.getKey();
                    it.remove();
                    if (ClientPlayNetworking.canSend(EchoSeenPayload.ID.id())) {
                        ClientPlayNetworking.send(new EchoSeenPayload(echoId));
                    }
                }
            }

            // Tick ghost state-machines (position / alpha advancement). No rendering here.
            if (!activeGhosts.isEmpty()) {
                Iterator<Map.Entry<UUID, ActiveGhost>> git = activeGhosts.entrySet().iterator();
                while (git.hasNext()) {
                    Map.Entry<UUID, ActiveGhost> entry = git.next();
                    GhostPlayerEntity ghost = entry.getValue().ghost();
                    ghost.tick();
                    if (ghost.isFinished()) {
                        git.remove();
                    }
                }
            }
        });

        // -----------------------------------------------------------------------
        // A1 + A2: WorldRenderEvents.AFTER_ENTITIES — all draw calls happen here.
        //
        // This gives us:
        // - MatrixStack (camera-relative)
        // - VertexConsumerProvider (for translucent geometry)
        // - sub-tick tickDelta (for smooth lerp)
        // -----------------------------------------------------------------------
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (activeGhosts.isEmpty())
                return;

            MatrixStack matrices = context.matrixStack();
            if (matrices == null)
                return;

            VertexConsumerProvider consumers = context.consumers();
            if (consumers == null)
                return;

            float tickDelta = context.tickCounter().getTickDelta(true);

            for (ActiveGhost ag : activeGhosts.values()) {
                if (ag.ghost().isFinished())
                    continue;

                // F6: resolve UUID for rendering (anonymize if needed)
                UUID resolvedUuid = ag.playerUuid();
                if (com.vardanrattan.echoes.config.EchoesConfig.get().isAnonymizeAll()) {
                    resolvedUuid = null;
                }

                GhostPlayerRenderer.renderGhost(
                        matrices,
                        consumers,
                        tickDelta,
                        ag.ghost(),
                        ag.tier(),
                        ag.anchor(),
                        ag.equipment(),
                        resolvedUuid);
            }
        });
    }

    // -------------------------------------------------------------------------
    // C1: Vanilla sound playback on echo start.
    // All sounds spatially anchored, SoundCategory.PLAYERS.
    // -------------------------------------------------------------------------
    private static void playEchoStartSound(EchoPlaybackPayload payload, net.minecraft.client.MinecraftClient client) {
        if (client.world == null)
            return;

        BlockPos anchor = payload.anchorPos();
        double ax = anchor.getX() + 0.5;
        double ay = anchor.getY();
        double az = anchor.getZ() + 0.5;

        // Primary tier sound.
        var primarySound = switch (payload.tier()) {
            case WHISPER -> SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME;
            case MARK -> SoundEvents.ENTITY_ENDERMAN_AMBIENT;
            case SCAR, WORLD_FIRST -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
        };
        float primaryVol = switch (payload.tier()) {
            case WHISPER -> 0.15f;
            case MARK -> 0.30f;
            case SCAR, WORLD_FIRST -> 0.50f;
        };

        // Use the Entity-based playSound overload: (Entity, x, y, z, sound, cat, vol, pitch)
        client.world.playSound(null, ax, ay, az, primarySound, SoundCategory.PLAYERS, primaryVol, 1.5f);

        // Death echoes additionally play the death sound pitched down.
        if (payload.eventType() == com.vardanrattan.echoes.data.EchoEventType.DEATH) {
            client.world.playSound(null, ax, ay, az,
                    SoundEvents.ENTITY_PLAYER_DEATH, SoundCategory.PLAYERS, 0.4f, 0.7f);
        }
    }

    /**
     * F5: Performs a client-side raycast against currently active ghosts.
     * Returns true if a ghost was hit and its metadata shown.
     */
    public static boolean tryShowGhostTooltip(net.minecraft.client.MinecraftClient client) {
        if (activeGhosts.isEmpty() || client.player == null) return false;

        Vec3d start = client.player.getEyePos();
        Vec3d dir = client.player.getRotationVec(1.0f);
        double range = 10.0;
        Vec3d end = start.add(dir.multiply(range));

        UUID hitId = null;
        double minDistance = Double.MAX_VALUE;

        for (var entry : activeGhosts.entrySet()) {
            ActiveGhost ag = entry.getValue();
            if (ag.ghost().isFinished()) continue;

            // Simplified collision check: Treat the ghost as a 0.6x1.8 box at its anchor.
            // In a real mod, we'd use the ghost's actual interpolated position.
            // Let's get the ghost's current interpolated world position.
            Vec3d ghostPos = ag.ghost().getInterpolatedPosition(ag.anchor(), 1.0f);
            
            // Box check
            net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(
                    ghostPos.x - 0.3, ghostPos.y, ghostPos.z - 0.3,
                    ghostPos.x + 0.3, ghostPos.y + 1.8, ghostPos.z + 0.3
            );

            java.util.Optional<Vec3d> hit = box.raycast(start, end);
            if (hit.isPresent()) {
                double dist = start.squaredDistanceTo(hit.get());
                if (dist < minDistance) {
                    minDistance = dist;
                    hitId = entry.getKey();
                }
            }
        }

        if (hitId != null) {
            ActiveGhost hitGhost = activeGhosts.get(hitId);
            showMetadataTooltip(client, hitGhost);
            return true;
        }

        return false;
    }

    private static void showMetadataTooltip(net.minecraft.client.MinecraftClient client, ActiveGhost ag) {
        // F5: player name, relative time, event type
        long ageMs = System.currentTimeMillis() - ag.startTimeMs();
        String relativeTime = formatRelativeTime(ageMs);

        // F6: Use EchoPrivacy-like logic for name resolution
        String displayName = ag.playerName();
        if (com.vardanrattan.echoes.config.EchoesConfig.get().isAnonymizeAll() || 
            com.vardanrattan.echoes.config.EchoesConfig.get().isHidePlayerNames()) {
            displayName = "Anonymous";
        }
        
        client.player.sendMessage(Text.empty()
                .append(Text.literal("Echo: ").formatted(Formatting.GOLD))
                .append(Text.literal(displayName).formatted(Formatting.WHITE))
                .append(Text.literal(" (").formatted(Formatting.GRAY))
                .append(Text.literal(ag.eventType().name()).formatted(Formatting.AQUA))
                .append(Text.literal(")").formatted(Formatting.GRAY)), false);

        client.player.sendMessage(Text.empty()
                .append(Text.literal("Recorded: ").formatted(Formatting.GOLD))
                .append(Text.literal(relativeTime).formatted(Formatting.WHITE)), false);
    }

    private static String formatRelativeTime(long ageMs) {
        long seconds = TimeUnit.MILLISECONDS.toSeconds(ageMs);
        if (seconds < 60) return seconds + "s ago";
        long minutes = TimeUnit.MILLISECONDS.toMinutes(ageMs);
        if (minutes < 60) return minutes + "m ago";
        long hours = TimeUnit.MILLISECONDS.toHours(ageMs);
        if (hours < 24) return hours + "h ago";
        long days = TimeUnit.MILLISECONDS.toDays(ageMs);
        return days + "d ago";
    }

    private static int estimateDurationTicks(EchoPlaybackPayload payload) {
        if (payload.frames() == null || payload.frames().isEmpty()) {
            return 20 * 5; // default 5s
        }
        int maxTickOffset = 0;
        for (var f : payload.frames()) {
            maxTickOffset = Math.max(maxTickOffset, f.getTickOffset());
        }
        // Add fade padding (~1.5s) and ensure non-zero.
        return Math.max(20, maxTickOffset + (20 * 2));
    }
}
