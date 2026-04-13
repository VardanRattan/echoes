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

        EchoesConfig cfg = EchoesConfig.get();
        float baseOpacity = switch (tier) {
            case WHISPER -> cfg.getWhisperOpacity();
            case MARK    -> cfg.getMarkOpacity();
            case SCAR, WORLD_FIRST -> cfg.getScarOpacity();
        };

        float effectiveAlpha = Mth.clamp(alpha * baseOpacity, 0.0f, 1.0f);
        GhostPlayerEntity.Pose pose = ghost.getCurrentPose();

        double wx = anchor.getX() + 0.5 + pose.x();
        double wy = anchor.getY() + pose.y();
        double wz = anchor.getZ() + 0.5 + pose.z();

        EntityRenderDispatcher erd = client.getEntityRenderDispatcher();
        Vec3 cam = client.gameRenderer.getMainCamera().position();

        poseStack.pushPose();
        poseStack.translate(wx - cam.x, wy - cam.y, wz - cam.z);

        float yaw   = pose.yaw();
        float pitch = pose.pitch();

        // Resolve the skin for this ghost's player UUID.
        // DefaultPlayerSkin.get(UUID) returns a PlayerSkin record directly.
        // The skin is used to populate AvatarRenderState.skin below.
        PlayerSkin skin = (playerUuid != null)
                ? DefaultPlayerSkin.get(playerUuid)
                : DefaultPlayerSkin.getDefaultSkin();

        AbstractClientPlayer standInPlayer = client.player;
        renderingGhost = true;
        try {
            AvatarRenderer<AbstractClientPlayer> playerRenderer =
                    erd.getPlayerRenderer(standInPlayer);

            AvatarRenderState state = playerRenderer.createRenderState();
            playerRenderer.extractRenderState(standInPlayer, state, tickDelta);

            // Feed ghost pose data into the render state
            state.bodyRot            = yaw;
            state.yRot               = yaw;
            state.xRot               = pitch;
            state.walkAnimationPos   = pose.limbSwing();
            state.walkAnimationSpeed = 0.8f;

            // Assign the resolved skin so the correct texture is used
            state.skin = skin;

            // For WHISPER tier ghosts, mark as invisible-to-player so the
            // model renders with the engine's built-in translucency pass.
            // For higher tiers we render fully and rely on particle density
            // to communicate the ghost's fade state.
            state.isInvisibleToPlayer = (tier == EchoTier.WHISPER && effectiveAlpha < 0.5f);

            // submit() replaces the old render(state, poseStack, consumers, light) call.
            // CameraRenderState is obtained from the level renderer context; we pass
            // null here and let the renderer fall back to its defaults — adjust if
            // your call site has access to a CameraRenderState instance.
            playerRenderer.submit(state, poseStack, submitCollector, null);

        } catch (Exception e) {
            // Fail gracefully — ghost simply doesn't render this frame
        } finally {
            renderingGhost = false;
        }

        poseStack.popPose();

        spawnGhostParticles(world, ghost, tier, wx, wy, wz);
    }

    // -------------------------------------------------------------------------
    // Particles
    // -------------------------------------------------------------------------

    private static void spawnGhostParticles(
            ClientLevel world,
            GhostPlayerEntity ghost,
            EchoTier tier,
            double wx, double wy, double wz) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.particleEngine == null)
            return;

        float alpha     = ghost.getAlpha();
        float intensity = Mth.clamp(alpha, 0.05f, 1.0f);

        switch (tier) {
            case WHISPER -> {
                if (world.getRandom().nextInt(12) < Math.round(intensity * 2)) {
                    createParticle(world, ParticleTypes.SOUL, wx, wy, wz, 0.0, 0.03, 0.0);
                }
            }
            case MARK -> {
                if (world.getRandom().nextInt(7) < Math.round(intensity * 3)) {
                    createParticle(world, ParticleTypes.SOUL,    wx, wy, wz, orbit(world), orbit(world), orbit(world));
                    createParticle(world, ParticleTypes.ENCHANT, wx, wy, wz, orbit(world), 0.04,         orbit(world));
                }
            }
            case SCAR, WORLD_FIRST -> {
                if (world.getRandom().nextInt(5) < Math.round(intensity * 4)) {
                    createParticle(world, ParticleTypes.SOUL,    wx, wy, wz, rand(world), 0.05, rand(world));
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
