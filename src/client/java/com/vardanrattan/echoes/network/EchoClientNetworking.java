package com.vardanrattan.echoes.network;

import com.vardanrattan.echoes.Echoes;
import com.vardanrattan.echoes.data.EchoTier;
import com.vardanrattan.echoes.data.EquipmentSnapshot;
import com.vardanrattan.echoes.entity.GhostPlayerEntity;
import com.vardanrattan.echoes.render.GhostPlayerRenderer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Client-side networking handlers.
 *
 * Updated for Mojang mappings (net.minecraft.client.renderer package).
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
                ClientLevel world = context.client().level;
                if (world == null) return;

                nearbyEchoesCache.clear();
                for (EchoSensePayload.SenseEntry entry : payload.nearbyEchoes()) {
                    nearbyEchoesCache.put(entry.echoId(), entry);
                    BlockPos pos = entry.pos();
                    // Local particle burst at each echo anchor.
                    for (int i = 0; i < 10; i++) {
                        context.client().particleEngine.createParticle(
                                ParticleTypes.SOUL,
                                (double) pos.getX() + 0.5 + (world.getRandom().nextDouble() - 0.5) * 0.8,
                                (double) pos.getY() + 1.0 + (world.getRandom().nextDouble() - 0.5) * 0.8,
                                (double) pos.getZ() + 0.5 + (world.getRandom().nextDouble() - 0.5) * 0.8,
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
            ClientLevel world = Minecraft.getInstance().level;
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
        // A1 + A2: LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES — all draw calls happen here.
        // -----------------------------------------------------------------------
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(context -> {
            if (activeGhosts.isEmpty())
                return;

            PoseStack poseStack = context.poseStack();
            if (poseStack == null)
                return;

            SubmitNodeCollector collector = context.submitNodeCollector();
            if (collector == null)
                return;

            float tickDelta = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);

            for (ActiveGhost ag : activeGhosts.values()) {
                if (ag.ghost().isFinished())
                    continue;

                // F6: resolve UUID for rendering (anonymize if needed)
                UUID resolvedUuid = ag.playerUuid();
                if (com.vardanrattan.echoes.config.EchoesConfig.get().isAnonymizeAll()) {
                    resolvedUuid = null;
                }

                GhostPlayerRenderer.renderGhost(
                        poseStack,
                        collector,
                        tickDelta,
                        ag.ghost(),
                        ag.tier(),
                        ag.anchor(),
                        ag.equipment(),
                        resolvedUuid);
            }
        });
    }

    private static void playEchoStartSound(EchoPlaybackPayload payload, Minecraft client) {
        if (client.level == null)
            return;

        BlockPos anchor = payload.anchorPos();
        double ax = anchor.getX() + 0.5;
        double ay = anchor.getY();
        double az = anchor.getZ() + 0.5;

        var primarySound = switch (payload.tier()) {
            case WHISPER -> SoundEvents.AMETHYST_BLOCK_CHIME;
            case MARK -> SoundEvents.ENDERMAN_AMBIENT;
            case SCAR, WORLD_FIRST -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
        };
        float primaryVol = switch (payload.tier()) {
            case WHISPER -> 0.15f;
            case MARK -> 0.30f;
            case SCAR, WORLD_FIRST -> 0.50f;
        };

        client.level.playSound(null, ax, ay, az, primarySound, SoundSource.PLAYERS, primaryVol, 1.5f);

        if (payload.eventType() == com.vardanrattan.echoes.data.EchoEventType.DEATH) {
            client.level.playSound(null, ax, ay, az,
                    SoundEvents.PLAYER_DEATH, SoundSource.PLAYERS, 0.4f, 0.7f);
        }
    }

    public static boolean tryShowGhostTooltip(Minecraft client) {
        if (activeGhosts.isEmpty() || client.player == null) return false;

        Vec3 start = client.player.getEyePosition();
        Vec3 dir = client.player.getLookAngle();
        double range = 10.0;
        Vec3 end = start.add(dir.scale(range));

        UUID hitId = null;
        double minDistance = Double.MAX_VALUE;

        for (var entry : activeGhosts.entrySet()) {
            ActiveGhost ag = entry.getValue();
            if (ag.ghost().isFinished()) continue;

            Vec3 ghostPos = ag.ghost().getInterpolatedPosition(ag.anchor(), 1.0f);
            
            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                    ghostPos.x - 0.3, ghostPos.y, ghostPos.z - 0.3,
                    ghostPos.x + 0.3, ghostPos.y + 1.8, ghostPos.z + 0.3
            );

            java.util.Optional<Vec3> hit = box.clip(start, end);
            if (hit.isPresent()) {
                double dist = start.distanceTo(hit.get());
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

    private static void showMetadataTooltip(Minecraft client, ActiveGhost ag) {
        long ageMs = System.currentTimeMillis() - ag.startTimeMs();
        String relativeTime = formatRelativeTime(ageMs);

        String displayName = ag.playerName();
        if (com.vardanrattan.echoes.config.EchoesConfig.get().isAnonymizeAll() || 
            com.vardanrattan.echoes.config.EchoesConfig.get().isHidePlayerNames()) {
            displayName = "Anonymous";
        }
        
        client.player.sendSystemMessage(Component.empty()
                .append(Component.literal("Echo: ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(displayName).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" (").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(ag.eventType().name()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(")").withStyle(ChatFormatting.GRAY)));

        client.player.sendSystemMessage(Component.empty()
                .append(Component.literal("Recorded: ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(relativeTime).withStyle(ChatFormatting.WHITE)));
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
        return Math.max(20, maxTickOffset + (20 * 2));
    }
}
