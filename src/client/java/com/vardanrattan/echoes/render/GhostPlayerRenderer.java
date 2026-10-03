package com.vardanrattan.echoes.render;

import com.vardanrattan.echoes.config.EchoesConfig;
import com.vardanrattan.echoes.data.EchoTier;
import com.vardanrattan.echoes.data.EquipmentSnapshot;
import com.vardanrattan.echoes.entity.GhostPlayerEntity;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.Identifier;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.rendertype.RenderTypes;

import net.minecraft.core.particles.ParticleTypes;
import java.util.UUID;

/**
 * Ghost player renderer.
 *
 * Updated for Minecraft 26.1.1 using Mojang mappings.
 *
 * Uses a custom translucent RenderType with the ghost_desaturate shader
 * for true per-vertex alpha blending. The shader desaturates the player
 * texture and applies ghost tinting and alpha via vertexColor.
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
            net.minecraft.client.renderer.state.level.CameraRenderState cameraState,
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

        GhostPlayerEntity.Pose pose = ghost.getInterpolatedPose(tickDelta);
        double wx = anchor.getX() + 0.5 + pose.x();
        double wy = anchor.getY() + pose.y();
        double wz = anchor.getZ() + 0.5 + pose.z();

        Vec3 camPos = (cameraState != null && cameraState.pos != null)
                ? cameraState.pos
                : client.gameRenderer.getMainCamera().position();

        double distSq = camPos.distanceToSqr(wx, wy, wz);
        if (distSq > 4096) {
            return;
        }

        PlayerSkin skin = (playerUuid != null)
                ? DefaultPlayerSkin.get(playerUuid)
                : DefaultPlayerSkin.getDefaultSkin();

        AvatarRenderState state = new AvatarRenderState();
        state.scale = 1.0f;
        state.bodyRot = pose.yaw();
        state.yRot = pose.yaw();
        state.xRot = pose.pitch();
        state.walkAnimationPos = pose.limbSwing();
        state.walkAnimationSpeed = 0.8f;
        state.skin = skin;
        state.showHat = true;
        state.showJacket = true;
        state.showLeftPants = true;
        state.showRightPants = true;
        state.showLeftSleeve = true;
        state.showRightSleeve = true;
        state.showCape = true;

        // Render as translucent spectator ghost
        state.isInvisible = true;
        state.isInvisibleToPlayer = false;
        state.lightCoords = 15728880;

        var animState = pose.animationState();
        if (animState != null) {
            switch (animState) {
                case CROUCHING -> state.isCrouching = true;
                case ELYTRA_FLYING -> state.isFallFlying = true;
                case SWIMMING -> state.isVisuallySwimming = true;
                case DYING -> {
                    float progress = Math.min((ghost.getCurrentTick() + tickDelta), 19.0f);
                    state.deathTime = progress;
                }
                default -> {}
            }
        }

        if (equipment != null) {
            state.headEquipment = equipment.getStack(EquipmentSnapshot.Slot.HEAD);
            state.chestEquipment = equipment.getStack(EquipmentSnapshot.Slot.CHEST);
            state.legsEquipment = equipment.getStack(EquipmentSnapshot.Slot.LEGS);
            state.feetEquipment = equipment.getStack(EquipmentSnapshot.Slot.FEET);
            state.rightHandItemStack = equipment.getStack(EquipmentSnapshot.Slot.MAIN_HAND);
            state.leftHandItemStack = equipment.getStack(EquipmentSnapshot.Slot.OFF_HAND);
        }

        renderingGhost = true;
        try {
            EntityRenderDispatcher erd = client.getEntityRenderDispatcher();
            erd.submit(
                    state,
                    cameraState,
                    wx - camPos.x,
                    wy - camPos.y,
                    wz - camPos.z,
                    poseStack,
                    submitCollector
            );
        } catch (Exception e) {
            com.vardanrattan.echoes.Echoes.LOGGER.error("Ghost render failed", e);
        } finally {
            renderingGhost = false;
        }
    }

    // -------------------------------------------------------------------------
    // Particles (Ticked at 20 Hz in ClientTickEvents, not during render frames)
    // -------------------------------------------------------------------------

    public static void spawnGhostParticles(
            ClientLevel world,
            GhostPlayerEntity ghost,
            EchoTier tier,
            BlockPos anchor) {
        GhostPlayerEntity.Pose pose = ghost.getCurrentPose();
        double wx = anchor.getX() + 0.5 + pose.x();
        double wy = anchor.getY() + pose.y();
        double wz = anchor.getZ() + 0.5 + pose.z();

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.particleEngine == null || mc.player == null)
            return;

        double distSq = mc.player.distanceToSqr(wx, wy, wz);
        if (distSq > 1024) return; // No particles beyond 32 blocks
        float distanceMultiplier = (distSq > 256) ? 0.25f : 1.0f;

        float alpha = ghost.getAlpha();
        float intensity = Mth.clamp(alpha, 0.05f, 1.0f) * distanceMultiplier;

        switch (tier) {
            case WHISPER -> {
                if (world.getRandom().nextInt(16) < Math.round(intensity * 2)) {
                    createParticle(world, ParticleTypes.SOUL, wx, wy + 0.2, wz, 0.0, 0.02, 0.0);
                }
            }
            case MARK -> {
                if (world.getRandom().nextInt(8) < Math.round(intensity * 3)) {
                    createParticle(world, ParticleTypes.SOUL, wx, wy + 0.2, wz, orbit(world), 0.02, orbit(world));
                    createParticle(world, ParticleTypes.ENCHANT, wx, wy + 0.8, wz, orbit(world), 0.04, orbit(world));
                }
            }
            case SCAR, WORLD_FIRST -> {
                if (world.getRandom().nextInt(5) < Math.round(intensity * 3)) {
                    createParticle(world, ParticleTypes.SOUL, wx, wy + 0.2, wz, rand(world), 0.03, rand(world));
                    createParticle(world, ParticleTypes.END_ROD, wx, wy + 0.8, wz, rand(world), 0.04, rand(world));
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
