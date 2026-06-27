package com.vardanrattan.echoes.render;

import com.vardanrattan.echoes.config.EchoesConfig;
import com.vardanrattan.echoes.data.EchoTier;
import com.vardanrattan.echoes.data.EquipmentSnapshot;
import com.vardanrattan.echoes.entity.GhostPlayerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.core.particles.ParticleTypes;
import java.util.UUID;

/**
 * Ghost player renderer.
 *
 * Updated for Minecraft 26.1.1 using Mojang mappings.
 *
 * NOTE ON ALPHA: The 26.1 rendering pipeline moved from MultiBufferSource
 * (interceptable per-vertex) to SubmitNodeCollector (baked geometry).
 * Per-vertex alpha injection via a wrapper is no longer possible at this
 * call site. Ghost transparency is currently expressed through particle
 * density and the isInvisibleToPlayer flag for WHISPER tier.
 * True alpha blending requires a custom RenderLayer — tracked as future work.
 */
public final class GhostPlayerRenderer {

    private static boolean renderingGhost = false;

    private GhostPlayerRenderer() {
    }

    public static boolean isRenderingGhost() {
        return renderingGhost;
    }

    public static void renderGhost(
            PoseStack poseStack,
            SubmitNodeCollector submitCollector,
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

        GhostPlayerEntity.Pose pose = ghost.getCurrentPose();
        double wx = anchor.getX() + 0.5 + pose.x();
        double wy = anchor.getY() + pose.y();
        double wz = anchor.getZ() + 0.5 + pose.z();

        Vec3 cam = client.gameRenderer.getMainCamera().position();
        double distSq = cam.distanceToSqr(wx, wy, wz);

        // OPTIMIZATION: Distance Culling (don't render models if > 64 blocks away)
        if (distSq > 4096) {
            return;
        }

        EchoesConfig cfg = EchoesConfig.get();
        float baseOpacity = switch (tier) {
            case WHISPER -> cfg.getWhisperOpacity();
            case MARK -> cfg.getMarkOpacity();
            case SCAR, WORLD_FIRST -> cfg.getScarOpacity();
        };

        float effectiveAlpha = Mth.clamp(alpha * baseOpacity, 0.0f, 1.0f);

        EntityRenderDispatcher erd = client.getEntityRenderDispatcher();
        
        poseStack.pushPose();
        poseStack.translate(wx - cam.x, wy - cam.y, wz - cam.z);

        float yaw = pose.yaw();
        float pitch = pose.pitch();

        PlayerSkin skin = (playerUuid != null)
                ? DefaultPlayerSkin.get(playerUuid)
                : DefaultPlayerSkin.getDefaultSkin();

        AbstractClientPlayer standInPlayer = client.player;
        renderingGhost = true;
        try {
            AvatarRenderer<AbstractClientPlayer> playerRenderer = erd.getPlayerRenderer(standInPlayer);

            // Create a fresh state instead of extracting from a live entity
            AvatarRenderState state = new AvatarRenderState();

            // Apply ghost pose data
            state.bodyRot = yaw;
            state.yRot = yaw;
            state.xRot = pitch;
            state.walkAnimationPos = pose.limbSwing();
            state.walkAnimationSpeed = 0.8f;
            state.skin = skin;

            // Fetch the model from the renderer
            var model = playerRenderer.getModel();
            model.setupAnim(state);

            // Safely get the texture identifier regardless of mappings (record method vs field)
            net.minecraft.resources.Identifier tex = null;
            try {
                tex = (net.minecraft.resources.Identifier) state.skin.getClass().getMethod("texture").invoke(state.skin);
            } catch (Exception e) {
                try {
                    tex = (net.minecraft.resources.Identifier) state.skin.getClass().getField("texture").get(state.skin);
                } catch (Exception e2) {
                    tex = net.minecraft.resources.Identifier.tryParse("minecraft:textures/entity/steve.png");
                }
            }

            // Safely get the translucent RenderType regardless of mappings
            RenderType renderType = null;
            try {
                for (java.lang.reflect.Method m : RenderType.class.getMethods()) {
                    if (m.getName().toLowerCase().contains("translucent") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == net.minecraft.resources.Identifier.class) {
                        renderType = (RenderType) m.invoke(null, tex);
                        break;
                    }
                }
                if (renderType == null) {
                    for (java.lang.reflect.Method m : RenderType.class.getMethods()) {
                        if (m.getName().toLowerCase().contains("translucent") && m.getParameterCount() == 2 && m.getParameterTypes()[0] == net.minecraft.resources.Identifier.class) {
                            renderType = (RenderType) m.invoke(null, tex, true);
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore
            }

            com.mojang.blaze3d.vertex.VertexConsumer buffer = client.renderBuffers().bufferSource().getBuffer(renderType);

            // TIER SPECIFIC COLORING
            int r, g, b;
            if (tier == EchoTier.WORLD_FIRST) {
                r = 255; g = 230; b = 150; // Ethereal Gold
            } else {
                r = 200; g = 220; b = 255; // Pale Blue
            }

            int a = (int) (effectiveAlpha * 255);
            int color = (a << 24) | (r << 16) | (g << 8) | b;

            // PULSING GLOW: Oscillate overlay brightness based on tick
            float pulse = (Mth.sin((ghost.getCurrentTick() + tickDelta) * 0.1f) + 1.0f) * 0.5f;
            int light = (int) (15728880 * (0.8f + (pulse * 0.2f))); // Very slight pulse in brightness
            light = Mth.clamp(light, 0, 15728880);

            model.renderToBuffer(poseStack, buffer, light, net.minecraft.client.renderer.entity.LivingEntityRenderer.getOverlayCoords(state, 0.0f), color);

        } catch (Exception e) {
            com.vardanrattan.echoes.Echoes.LOGGER.error("Ghost render failed", e);
        } finally {
            renderingGhost = false;
        }

        poseStack.popPose();

        spawnGhostParticles(world, ghost, tier, wx, wy, wz, distSq);
    }

    // -------------------------------------------------------------------------
    // Particles
    // -------------------------------------------------------------------------

    private static void spawnGhostParticles(
            ClientLevel world,
            GhostPlayerEntity ghost,
            EchoTier tier,
            double wx, double wy, double wz,
            double distSq) {
            
        // OPTIMIZATION: Distance-based particle throttling
        if (distSq > 1024) return; // No particles beyond 32 blocks
        float distanceMultiplier = (distSq > 256) ? 0.25f : 1.0f; // 25% particles beyond 16 blocks

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.particleEngine == null)
            return;

        float alpha = ghost.getAlpha();
        float intensity = Mth.clamp(alpha, 0.05f, 1.0f) * distanceMultiplier;

        switch (tier) {
            case WHISPER -> {
                if (world.getRandom().nextInt(12) < Math.round(intensity * 2)) {
                    createParticle(world, ParticleTypes.SOUL, wx, wy, wz, 0.0, 0.03, 0.0);
                }
            }
            case MARK -> {
                if (world.getRandom().nextInt(7) < Math.round(intensity * 3)) {
                    createParticle(world, ParticleTypes.SOUL, wx, wy, wz, orbit(world), orbit(world), orbit(world));
                    createParticle(world, ParticleTypes.ENCHANT, wx, wy, wz, orbit(world), 0.04, orbit(world));
                }
            }
            case SCAR, WORLD_FIRST -> {
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
        Minecraft mc = Minecraft.getInstance();
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
}
