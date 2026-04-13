package com.vardanrattan.echoes.entity;

import com.vardanrattan.echoes.data.EchoFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Client-side playback controller for a single ghost.
 *
 * This is not yet wired into Minecraft's entity system; it is a pure data/logic
 * object that steps through EchoFrame data and exposes interpolated pose +
 * opacity for rendering.
 */
public final class GhostPlayerEntity {

    public record Pose(float x, float y, float z, float yaw, float pitch, float limbSwing, com.vardanrattan.echoes.data.EchoAnimState animationState) {
    }

    private final List<EchoFrame> frames;
    private final int totalDurationTicks;
    private final int fadeInTicks;
    private final int fadeOutTicks;

    private int currentTick;
    private boolean finished;

    public GhostPlayerEntity(List<EchoFrame> frames, int fadeInTicks, int fadeOutTicks) {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("frames must not be empty");
        }
        this.frames = List.copyOf(frames);
        int maxOffset = 0;
        for (EchoFrame frame : frames) {
            maxOffset = Math.max(maxOffset, frame.getTickOffset());
        }
        this.totalDurationTicks = maxOffset + 1;
        this.fadeInTicks = Math.max(1, fadeInTicks);
        this.fadeOutTicks = Math.max(1, fadeOutTicks);
    }

    public void tick() {
        if (finished) {
            return;
        }
        currentTick++;
        if (currentTick >= totalDurationTicks + fadeOutTicks) {
            finished = true;
        }
    }

    public boolean isFinished() {
        return finished;
    }

    /**
     * Returns current opacity (0–1) based on fade in/out windows.
     */
    public float getAlpha() {
        if (finished) {
            return 0.0f;
        }
        if (currentTick <= fadeInTicks) {
            return currentTick / (float) fadeInTicks;
        }
        int playbackEnd = totalDurationTicks;
        if (currentTick >= playbackEnd) {
            int t = currentTick - playbackEnd;
            if (t >= fadeOutTicks) {
                return 0.0f;
            }
            return 1.0f - (t / (float) fadeOutTicks);
        }
        return 1.0f;
    }

    public Pose getCurrentPose() {
        return getInterpolatedPose(1.0f);
    }

    public Vec3 getInterpolatedPosition(BlockPos anchor, float tickDelta) {
        Pose pose = getInterpolatedPose(tickDelta);
        return new Vec3(
                anchor.getX() + pose.x(),
                anchor.getY() + pose.y(),
                anchor.getZ() + pose.z()
        );
    }

    private Pose getInterpolatedPose(float tickDelta) {
        float renderTick = currentTick + tickDelta;

        if (frames.size() == 1) {
            EchoFrame f = frames.get(0);
            return new Pose(f.getRelX(), f.getRelY(), f.getRelZ(), f.getYaw(), f.getPitch(), f.getLimbSwing(), f.getAnimationState());
        }

        EchoFrame prev = frames.get(0);
        EchoFrame next = frames.get(frames.size() - 1);

        for (int i = 1; i < frames.size(); i++) {
            EchoFrame f = frames.get(i);
            if (f.getTickOffset() >= renderTick) {
                next = f;
                prev = frames.get(i - 1);
                break;
            }
        }

        int dt = Math.max(1, next.getTickOffset() - prev.getTickOffset());
        float t = Math.clamp((renderTick - prev.getTickOffset()) / (float) dt, 0.0f, 1.0f);

        float x = lerp(prev.getRelX(), next.getRelX(), t);
        float y = lerp(prev.getRelY(), next.getRelY(), t);
        float z = lerp(prev.getRelZ(), next.getRelZ(), t);
        float yaw = lerpAngle(prev.getYaw(), next.getYaw(), t);
        float pitch = lerp(prev.getPitch(), next.getPitch(), t);
        float limbSwing = lerp(prev.getLimbSwing(), next.getLimbSwing(), t);

        return new Pose(x, y, z, yaw, pitch, limbSwing, prev.getAnimationState());
    }

    public int getCurrentTick() {
        return currentTick;
    }

    public int getTotalDurationTicks() {
        return totalDurationTicks;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float lerpAngle(float a, float b, float t) {
        float delta = wrapDegrees(b - a);
        return a + delta * t;
    }

    private static float wrapDegrees(float degrees) {
        degrees = degrees % 360.0f;
        if (degrees >= 180.0f) {
            degrees -= 360.0f;
        }
        if (degrees < -180.0f) {
            degrees += 360.0f;
        }
        return degrees;
    }
}
