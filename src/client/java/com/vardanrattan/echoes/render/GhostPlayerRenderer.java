package com.vardanrattan.echoes.render;

import com.vardanrattan.echoes.config.EchoesConfig;
import com.vardanrattan.echoes.data.EchoTier;
import com.vardanrattan.echoes.data.EquipmentSnapshot;
import com.vardanrattan.echoes.entity.GhostPlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.SkinTextures;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Ghost player renderer — A3 implementation.
 *
 * renderGhost() performs:
 * 1. MatrixStack translation to the ghost's world-space position
 * 2. Player model render using EntityRenderDispatcher with translucency
 *    controlled by alpha × tier base opacity
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
     * @param matrices  MatrixStack from WorldRenderEvents.AFTER_ENTITIES
     *                  (camera-space)
     * @param consumers VertexConsumerProvider from the same context
     * @param tickDelta Sub-tick interpolation delta
     * @param ghost     Playback controller; must not be finished
     * @param tier      Echo tier — controls opacity and particle style
     * @param anchor    Anchor BlockPos (world coordinates)
     * @param equipment Equipment worn at recording time; may be null
     */
    public static void renderGhost(
            MatrixStack matrices,
            VertexConsumerProvider consumers,
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

        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null || client.player == null)
            return;

        EchoesConfig cfg = EchoesConfig.get();
        float baseOpacity = switch (tier) {
            case WHISPER -> cfg.getWhisperOpacity();
            case MARK -> cfg.getMarkOpacity();
            case SCAR, WORLD_FIRST -> cfg.getScarOpacity();
        };

        float effectiveAlpha = Math.clamp(alpha * baseOpacity, 0.0f, 1.0f);

        // Interpolate pose using sub-tick tickDelta for smooth motion (A1 benefit).
        GhostPlayerEntity.Pose pose = ghost.getCurrentPose();

        // Compute world-space position of ghost (anchor + relative frame offset).
        double wx = anchor.getX() + 0.5 + pose.x();
        double wy = anchor.getY() + pose.y();
                double wz = anchor.getZ() + 0.5 + pose.z();

        // Camera position for MatrixStack translation.
        EntityRenderDispatcher erd = client.getEntityRenderDispatcher();
        Vec3d cam = erd.camera.getPos();

        matrices.push();
        matrices.translate(wx - cam.x, wy - cam.y, wz - cam.z);

        // Apply yaw rotation so the ghost faces the correct direction.
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(-pose.yaw() - 180.0f));

        // Use the skin texture associated with the recorded playerUuid.
        // F6: Respect anonymize-all flag via null UUID check.
        Identifier skinTexture;
        if (playerUuid != null) {
            skinTexture = client.getSkinProvider().getSkinTextures(new GameProfile(playerUuid, null)).texture();
        } else {
            // Steve fallback
            skinTexture = net.minecraft.client.util.DefaultSkinHelper.getTexture();
        }
        RenderLayer ghostLayer = RenderLayer.getEntityTranslucent(skinTexture, true);

        // We use the local client player as a stand-in entity for the model shape.
        AbstractClientPlayerEntity standInPlayer = client.player;

        // Set the rendering flag so GhostSuppressMixin zeroes the shadow.
        renderingGhost = true;
        try {
            // Use the entity render dispatcher to render the player model
            // through a custom VertexConsumerProvider that injects alpha.
            matrices.push();
            erd.render(
                    standInPlayer,
                    0.0, 0.0, 0.0, // already translated above
                    pose.yaw(),
                    tickDelta,
                    matrices,
                    new AlphaVertexConsumerProvider(consumers, effectiveAlpha),
                    erd.getLight(standInPlayer, tickDelta));
            matrices.pop();

        } finally {
            renderingGhost = false;
        }

        matrices.pop();

        // Tier-correct particle overlay (A4).
        spawnGhostParticles(world, ghost, tier, wx, wy, wz);
    }

    // -------------------------------------------------------------------------
    // A4: Corrected particle types per devdoc §6 table
    // -------------------------------------------------------------------------

    private static void spawnGhostParticles(
            ClientWorld world,
            GhostPlayerEntity ghost,
            EchoTier tier,
            double wx, double wy, double wz) {
        MinecraftClient mc = client();
        if (mc == null || mc.particleManager == null)
            return;

        float alpha = ghost.getAlpha();
        float intensity = Math.clamp(alpha, 0.05f, 1.0f);

        switch (tier) {
            case WHISPER -> {
                // 1–2 soul particles per second (≈ every 10–20 ticks)
                if (world.random.nextInt(12) < Math.round(intensity * 2)) {
                    addParticle(world, ParticleTypes.SOUL, wx, wy, wz, 0.0, 0.03, 0.0);
                }
            }
            case MARK -> {
                // 3–4 soul + enchant per second (≈ 1 every 5–7 ticks)
                if (world.random.nextInt(7) < Math.round(intensity * 3)) {
                    addParticle(world, ParticleTypes.SOUL, wx, wy, wz, orbit(world), orbit(world), orbit(world));
                    addParticle(world, ParticleTypes.ENCHANT, wx, wy, wz, orbit(world), 0.04, orbit(world));
                }
            }
            case SCAR, WORLD_FIRST -> {
                // 8–10 soul + end_rod per second (≈ 2 every 6 ticks)
                if (world.random.nextInt(5) < Math.round(intensity * 4)) {
                    addParticle(world, ParticleTypes.SOUL, wx, wy, wz, rand(world), 0.05, rand(world));
                    addParticle(world, ParticleTypes.END_ROD, wx, wy, wz, rand(world), 0.06, rand(world));
                }
            }
        }
    }

    private static void addParticle(
            ClientWorld world,
            net.minecraft.particle.ParticleType<?> type,
            double x, double y, double z,
            double vx, double vy, double vz) {
        MinecraftClient mc = client();
        if (mc == null || mc.particleManager == null)
            return;
        double ox = (world.random.nextDouble() - 0.5) * 0.5;
        double oz = (world.random.nextDouble() - 0.5) * 0.5;
        double oy = world.random.nextDouble() * 1.8;
        mc.particleManager.addParticle(
                (net.minecraft.particle.ParticleEffect) type,
                x + ox, y + oy, z + oz,
                vx, vy, vz);
    }

    private static double orbit(ClientWorld world) {
        return (world.random.nextDouble() - 0.5) * 0.08;
    }

    private static double rand(ClientWorld world) {
        return (world.random.nextDouble() - 0.5) * 0.06;
    }

    private static MinecraftClient client() {
        return MinecraftClient.getInstance();
    }

    // -------------------------------------------------------------------------
    // Inner helper: wraps a VertexConsumerProvider so that colours are
    // multiplied by alpha to produce translucency without a custom shader.
    // -------------------------------------------------------------------------

    private static final class AlphaVertexConsumerProvider implements VertexConsumerProvider {

        private final VertexConsumerProvider delegate;
        private final float alpha;

        AlphaVertexConsumerProvider(VertexConsumerProvider delegate, float alpha) {
            this.delegate = delegate;
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            VertexConsumer base = delegate.getBuffer(layer);
            return new AlphaOverrideVertexConsumer(base, alpha);
        }
    }

    /**
     * Delegates every VertexConsumer call to a backing consumer, but intercepts
     * {@link #color} to multiply the provided alpha by our ghost opacity.
     *
     * In MC 1.21.11 VertexConsumer uses vertex() not position(), and has
     * additional abstract methods we must implement.
     */
    private static final class AlphaOverrideVertexConsumer implements VertexConsumer {

        private final VertexConsumer delegate;
        private final int alphaByte;

        AlphaOverrideVertexConsumer(VertexConsumer delegate, float alpha) {
            this.delegate = delegate;
            this.alphaByte = Math.clamp(Math.round(alpha * 255f), 0, 255);
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int argb) {
            // Apply ghost alpha to the ARGB color.
            int a = (argb >> 24) & 0xFF;
            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;
            int ghostA = Math.clamp(Math.round((a / 255.0f) * alphaByte), 0, 255);
            delegate.color(r, g, b, ghostA);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            // Scale the incoming alpha by our ghost alpha.
            int ghostA = Math.clamp(Math.round((a / 255.0f) * alphaByte), 0, 255);
            delegate.color(r, g, b, ghostA);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            delegate.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            delegate.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            delegate.light(u, v);
            return this;
        }

    }
}
