package com.vardanrattan.echoes.data;

/**
 * Single frame of an echo recording, stored relative to the anchor position.
 */
public final class EchoFrame {
    private final float relX;
    private final float relY;
    private final float relZ;
    private final float yaw;
    private final float pitch;
    private final float limbSwing;
    private final EchoAnimState animationState;
    private final int tickOffset;

    public EchoFrame(
            float relX,
            float relY,
            float relZ,
            float yaw,
            float pitch,
            float limbSwing,
            EchoAnimState animationState,
            int tickOffset) {
        // Clamp all numeric fields to safe ranges so corrupt save data cannot
        // produce NaN/Inf values or extreme offsets that break the renderer.
        this.relX = Math.clamp(relX, -512f, 512f);
        this.relY = Math.clamp(relY, -512f, 512f);
        this.relZ = Math.clamp(relZ, -512f, 512f);
        this.yaw = yaw % 360.0f;
        this.pitch = Math.clamp(pitch, -90.0f, 90.0f);
        this.limbSwing = limbSwing;
        this.animationState = animationState;
        this.tickOffset = Math.max(0, tickOffset);
    }

    public float getRelX() {
        return relX;
    }

    public float getRelY() {
        return relY;
    }

    public float getRelZ() {
        return relZ;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public float getLimbSwing() {
        return limbSwing;
    }

    public EchoAnimState getAnimationState() {
        return animationState;
    }

    public int getTickOffset() {
        return tickOffset;
    }

    @Override
    public String toString() {
        return "EchoFrame{tick=" + tickOffset
                + ", rel=(" + relX + "," + relY + "," + relZ + ")"
                + ", yaw=" + yaw + ", pitch=" + pitch
                + ", anim=" + animationState + "}";
    }
}
