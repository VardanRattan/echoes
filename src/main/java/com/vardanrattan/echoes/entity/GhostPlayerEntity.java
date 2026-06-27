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

        int p1Idx = 0;
        int p2Idx = frames.size() - 1;

        for (int i = 1; i < frames.size(); i++) {
            if (frames.get(i).getTickOffset() >= renderTick) {
                p2Idx = i;
                p1Idx = i - 1;
                break;
            }
        }

        int p0Idx = Math.max(0, p1Idx - 1);
        int p3Idx = Math.min(frames.size() - 1, p2Idx + 1);

        EchoFrame p0 = frames.get(p0Idx);
        EchoFrame p1 = frames.get(p1Idx);
        EchoFrame p2 = frames.get(p2Idx);
        EchoFrame p3 = frames.get(p3Idx);

        int dt = Math.max(1, p2.getTickOffset() - p1.getTickOffset());
        float t = Math.clamp((renderTick - p1.getTickOffset()) / (float) dt, 0.0f, 1.0f);

        float x = catmullRom(p0.getRelX(), p1.getRelX(), p2.getRelX(), p3.getRelX(), t);
        float y = catmullRom(p0.getRelY(), p1.getRelY(), p2.getRelY(), p3.getRelY(), t);
        float z = catmullRom(p0.getRelZ(), p1.getRelZ(), p2.getRelZ(), p3.getRelZ(), t);
        float yaw = slerpAngle(p0.getYaw(), p1.getYaw(), p2.getYaw(), p3.getYaw(), t);
        float pitch = catmullRom(p0.getPitch(), p1.getPitch(), p2.getPitch(), p3.getPitch(), t);
        float limbSwing = lerp(p1.getLimbSwing(), p2.getLimbSwing(), t);

        return new Pose(x, y, z, yaw, pitch, limbSwing, p1.getAnimationState());
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

    private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5f * ((2.0f * p1) +
                (-p0 + p2) * t +
                (2.0f * p0 - 5.0f * p1 + 4.0f * p2 - p3) * t2 +
                (-p0 + 3.0f * p1 - 3.0f * p2 + p3) * t3);
    }

    private static float slerpAngle(float p0, float p1, float p2, float p3, float t) {
        // Catmull-Rom for angles requires wrapping the differences relative to p1
        float dp0 = wrapDegrees(p0 - p1);
        float dp1 = 0; // p1 - p1
        float dp2 = wrapDegrees(p2 - p1);
        float dp3 = wrapDegrees(p3 - p1);
        float result = catmullRom(dp0, dp1, dp2, dp3, t);
        return wrapDegrees(p1 + result);
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
