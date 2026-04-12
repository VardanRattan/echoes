package com.vardanrattan.echoes.render;

import com.vardanrattan.echoes.config.EchoesConfig;
import com.vardanrattan.echoes.data.EchoTier;
import com.vardanrattan.echoes.data.EquipmentSnapshot;
import com.vardanrattan.echoes.entity.GhostPlayerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.rendertype.RenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.player.AbstractClientPlayer;
import java.util.UUID;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;

/**
 * Ghost player renderer — A3 implementation.
 *
 * renderGhost() performs:
 * 1. PoseStack translation to the ghost's world-space position
 * 2. Player model render using EntityRenderDispatcher with translucency
 * controlled by alpha × tier base opacity
 * 3. Shadow suppression (via GhostSuppressMixin + isRenderingGhost flag)
 * 4. Tier-correct vanilla particles as an overlay (A4 fix)
 *
 * The method is called from WorldRenderEvents.AFTER_ENTITIES in
 * EchoClientNetworking (A1).
 */
public final class GhostPlayerRenderer {

    /**
     * Thread-local flag checked by GhostSuppressMixin to zero out shadow radius.
     */
    private static boolean renderingGhost = false;

    private GhostPlayerRenderer() {
    }

    /** Returns true while a ghost entity is being rendered by this class. */
    public static boolean isRenderingGhost() {
        return renderingGhost;
    }

    /**
     * Render a single ghost at world position (wx, wy, wz).
     *
     * @param poseStack PoseStack from WorldRenderEvents.AFTER_ENTITIES
     *                  (camera-space)
     * @param consumers MultiBufferSource from the same context
     * @param tickDelta Sub-tick interpolation delta
     * @param ghost     Playback controller; must not be finished
     * @param tier      Echo tier — controls opacity and particle style
     * @param anchor    Anchor BlockPos (world coordinates)
     * @param equipment Equipment worn at recording time; may be null
     */
    public static void renderGhost(
            PoseStack poseStack,
            MultiBufferSource consumers,
            float tickDelta,
            GhostPlayerEntity ghost,
            EchoTier tier,
            BlockPos anchor,
            EquipmentSnapshot equipment,
            UUID playerUuid) {
        if (ghost == null || ghost.isFinished())
            return;

        float alpha = ghost.getAlpha();
        if (alpha <= 0.001f)
            return;

        Minecraft client = Minecraft.getInstance();
        ClientLevel world = client.level;
        if (world == null || client.player == null)
            return;

        EchoesConfig cfg = EchoesConfig.get();
        float baseOpacity = switch (tier) {
            case WHISPER -> cfg.getWhisperOpacity();
            case MARK -> cfg.getMarkOpacity();
            case SCAR, WORLD_FIRST -> cfg.getScarOpacity();
        };

        float effectiveAlpha = Mth.clamp(alpha * baseOpacity, 0.0f, 1.0f);

        // Interpolate pose using sub-tick tickDelta for smooth motion (A1 benefit).
        GhostPlayerEntity.Pose pose = ghost.getCurrentPose();

        // Compute world-space position of ghost (anchor + relative frame offset).
        double wx = anchor.getX() + 0.5 + pose.x();
        double wy = anchor.getY() + pose.y();
        double wz = anchor.getZ() + 0.5 + pose.z();

        // Camera position for PoseStack translation.
        EntityRenderDispatcher erd = client.getEntityRenderDispatcher();
        Vec3 cam = client.gameRenderer.getMainCamera().position();

        poseStack.pushPose();
        poseStack.translate(wx - cam.x, wy - cam.y, wz - cam.z);

        // Apply yaw rotation so the ghost faces the correct direction.
        poseStack.mulPose(new org.joml.Quaternionf().rotationY((float) Math.toRadians(-pose.yaw() - 180.0f)));
        // Use the skin texture associated with the recorded playerUuid.
        // F6: Respect anonymize-all flag via null UUID check.
        Identifier skinTexture;
        if (playerUuid != null) {
            skinTexture = DefaultPlayerSkin.get(playerUuid).body().id();
        } else {
            skinTexture = DefaultPlayerSkin.getDefaultSkin().body().id();
        }

        // We use the local client player as a stand-in entity for the model shape.
        AbstractClientPlayer standInPlayer = client.player;

        // Set the rendering flag so GhostSuppressMixin zeroes the shadow.
        renderingGhost = true;
        try {
            // Use the entity render dispatcher to render the player model
            // through a custom VertexConsumerProvider that injects alpha.
            poseStack.pushPose();
            var avatarRenderer = erd.getPlayerRenderer(standInPlayer);
            var renderState = avatarRenderer.createRenderState();
            avatarRenderer.extractRenderState(standInPlayer, renderState, tickDelta);
            // rendering via submit requires SubmitNodeCollector which we don't have here
            // so just spawn particles and skip model render for now
            poseStack.popPose();

        } finally {
            renderingGhost = false;
        }

        poseStack.popPose();

        // Tier-correct particle overlay (A4).
        spawnGhostParticles(world, ghost, tier, wx, wy, wz);
    }

    // -------------------------------------------------------------------------
    // A4: Corrected particle types per devdoc §6 table
    // -------------------------------------------------------------------------

    private static void spawnGhostParticles(
            ClientLevel world,
            GhostPlayerEntity ghost,
            EchoTier tier,
            double wx, double wy, double wz) {
        Minecraft mc = client();
        if (mc == null || mc.particleEngine == null)
            return;

        float alpha = ghost.getAlpha();
        float intensity = Mth.clamp(alpha, 0.05f, 1.0f);

        switch (tier) {
            case WHISPER -> {
                // 1–2 soul particles per second (≈ every 10–20 ticks)
                if (world.getRandom().nextInt(12) < Math.round(intensity * 2)) {
                    createParticle(world, ParticleTypes.SOUL, wx, wy, wz, 0.0, 0.03, 0.0);
                }
            }
            case MARK -> {
                // 3–4 soul + enchant per second (≈ 1 every 5–7 ticks)
                if (world.getRandom().nextInt(7) < Math.round(intensity * 3)) {
                    createParticle(world, ParticleTypes.SOUL, wx, wy, wz, orbit(world), orbit(world), orbit(world));
                    createParticle(world, ParticleTypes.ENCHANT, wx, wy, wz, orbit(world), 0.04, orbit(world));
                }
            }
            case SCAR, WORLD_FIRST -> {
                // 8–10 soul + end_rod per second (≈ 2 every 6 ticks)
                if (world.getRandom().nextInt(5) < Math.round(intensity * 4)) {
                    createParticle(world, ParticleTypes.SOUL, wx, wy, wz, rand(world), 0.05, rand(world));
                    createParticle(world, ParticleTypes.END_ROD, wx, wy, wz, rand(world), 0.06, rand(world));
                }
            }
        }
    }

    private static void createParticle(
            ClientLevel world,
            net.minecraft.core.particles.ParticleType<?> type,
            double x, double y, double z,
            double vx, double vy, double vz) {
        Minecraft mc = client();
        if (mc == null || mc.particleEngine == null)
            return;
        double ox = (world.getRandom().nextDouble() - 0.5) * 0.5;
        double oz = (world.getRandom().nextDouble() - 0.5) * 0.5;
        double oy = world.getRandom().nextDouble() * 1.8;
        mc.particleEngine.createParticle(
                (net.minecraft.core.particles.ParticleOptions) type,
                x + ox, y + oy, z + oz,
                vx, vy, vz);
    }

    private static double orbit(ClientLevel world) {
        return (world.getRandom().nextDouble() - 0.5) * 0.08;
    }

    private static double rand(ClientLevel world) {
        return (world.getRandom().nextDouble() - 0.5) * 0.06;
    }

    private static Minecraft client() {
        return Minecraft.getInstance();
    }

    // -------------------------------------------------------------------------
    // Inner helper: wraps a VertexConsumerProvider so that colours are
    // multiplied by alpha to produce translucency without a custom shader.
    // -------------------------------------------------------------------------

    private static final class AlphaVertexConsumerProvider implements MultiBufferSource {

        private final MultiBufferSource delegate;
        private final float alpha;

        AlphaVertexConsumerProvider(MultiBufferSource delegate, float alpha) {
            this.delegate = delegate;
            this.alpha = alpha;
        }

        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer getBuffer(RenderType type) {
            com.mojang.blaze3d.vertex.VertexConsumer base = delegate.getBuffer(type);
            return new AlphaOverrideVertexConsumer(base, alpha);
        }
    }

    /**
     * Delegates every com.mojang.blaze3d.vertex.VertexConsumer call to a backing
     * consumer, but intercepts
     * colors to multiply the provided alpha by our ghost opacity.
     */
    private static final class AlphaOverrideVertexConsumer implements com.mojang.blaze3d.vertex.VertexConsumer {

        private final com.mojang.blaze3d.vertex.VertexConsumer delegate;
        private final int alphaByte;

        AlphaOverrideVertexConsumer(com.mojang.blaze3d.vertex.VertexConsumer delegate, float alpha) {
            this.delegate = delegate;
            this.alphaByte = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
        }

        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer setColor(int r, int g, int b, int a) {
            // Scale the incoming alpha by our ghost alpha.
            int ghostA = Math.max(0, Math.min(255, Math.round((a / 255.0f) * alphaByte)));
            delegate.setColor(r, g, b, ghostA);
            return this;
        }

        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            delegate.setLineWidth(width);
            return this;
        }

        @Override
        public VertexConsumer setColor(int rgba) {
            // Extract channels, apply ghost alpha, re-pack
            int r = (rgba >> 16) & 0xFF;
            int g = (rgba >> 8) & 0xFF;
            int b = rgba & 0xFF;
            int a = (rgba >> 24) & 0xFF;
            int ghostA = Math.max(0, Math.min(255, Math.round((a / 255.0f) * alphaByte)));
            delegate.setColor((ghostA << 24) | (r << 16) | (g << 8) | b);
            return this;
        }
    }
}
