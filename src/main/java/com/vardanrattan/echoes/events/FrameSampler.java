package com.vardanrattan.echoes.events;

import com.vardanrattan.echoes.data.EchoAnimState;
import com.vardanrattan.echoes.data.EchoFrame;
import com.vardanrattan.echoes.mixin.LimbAnimatorMixin;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Captures lightweight player snapshots for echo recording.
 *
 * Kept server-side and intentionally minimal: enough to reconstruct a recognizable
 * ghost while staying robust across Minecraft version changes.
 */
public final class FrameSampler {

    private FrameSampler() {
    }

    /**
     * Capture an absolute-position frame for a rolling buffer.
     * Returns null if capture fails or produces invalid values.
     */
    public static BufferedFrame captureBufferedFrame(ServerPlayerEntity player) {
        if (player == null || player.isRemoved()) {
            return null;
        }
        return captureFromSnapshot((float) player.getX(), (float) player.getY(), (float) player.getZ(), player.getYaw(), player.getPitch(), player);
    }

    /**
     * Capture a frame using provided position/rotation, but sampling the player's
     * animation state and limb swing directly (D1 async support).
     */
    public static BufferedFrame captureFromSnapshot(float x, float y, float z, float yaw, float pitch, ServerPlayerEntity player) {
        if (player == null || player.isRemoved()) {
            return null;
        }

        try {
            // B2: Read real limb swing via LimbAnimatorMixin @Accessor.
            // Cast is safe — Mixin weaves getPos() onto the LimbAnimator instance at load time.
            float limbSwing = ((LimbAnimatorMixin) player.limbAnimator).getPos();

            EchoAnimState animState = inferAnimState(player);

            if (!isFinite(x) || !isFinite(y) || !isFinite(z) || !isFinite(yaw) || !isFinite(pitch)) {
                return null;
            }

            return new BufferedFrame(x, y, z, yaw, pitch, limbSwing, animState);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Convert a buffered frame (absolute coords) into an EchoFrame (relative coords).
     * Returns null if inputs are invalid.
     */
    public static EchoFrame toRelativeEchoFrame(BufferedFrame bufferedFrame, BlockPos anchorPos, int tickOffset) {
        if (bufferedFrame == null || anchorPos == null) {
            return null;
        }

        float anchorX = anchorPos.getX() + 0.5f;
        float anchorY = anchorPos.getY();
        float anchorZ = anchorPos.getZ() + 0.5f;

        float relX = bufferedFrame.x() - anchorX;
        float relY = bufferedFrame.y() - anchorY;
        float relZ = bufferedFrame.z() - anchorZ;

        if (!isFinite(relX) || !isFinite(relY) || !isFinite(relZ)) {
            return null;
        }

        return new EchoFrame(
                relX,
                relY,
                relZ,
                bufferedFrame.yaw(),
                bufferedFrame.pitch(),
                bufferedFrame.limbSwing(),
                bufferedFrame.animationState(),
                tickOffset
        );
    }

    private static EchoAnimState inferAnimState(ServerPlayerEntity player) {
        // Note: on AFTER_DEATH this might already be true, but it’s fine.
        if (player.isDead()) {
            return EchoAnimState.DYING;
        }
        if (!player.isOnGround() && player.getVelocity().y < 0) {
            return EchoAnimState.FALLING;
        }
        if (player.isSprinting()) {
            return EchoAnimState.RUNNING;
        }
        if (player.getVelocity().horizontalLength() > 0.01) {
            return EchoAnimState.WALKING;
        }
        return EchoAnimState.IDLE;
    }

    private static boolean isFinite(float v) {
        return !Float.isNaN(v) && !Float.isInfinite(v);
    }

    /**
     * Helper to create a single-frame echo at the player's current position.
     */
    public static java.util.List<EchoFrame> singleFrame(net.minecraft.server.world.ServerWorld world, ServerPlayerEntity player) {
        var buffered = captureBufferedFrame(player);
        if (buffered == null) return java.util.Collections.emptyList();
        BlockPos anchor = player.getBlockPos();
        EchoFrame frame = toRelativeEchoFrame(buffered, anchor, 0);
        if (frame == null) return java.util.Collections.emptyList();
        java.util.List<EchoFrame> frames = new java.util.ArrayList<>(1);
        frames.add(frame);
        return frames;
    }
}

