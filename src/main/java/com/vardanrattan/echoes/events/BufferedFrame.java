package com.vardanrattan.echoes.events;

import com.vardanrattan.echoes.data.EchoAnimState;

/**
 * Raw frame data captured before we know the anchor position.
 * Converted to EchoFrame with relative coordinates when we finalize.
 */
record BufferedFrame(
        float x,
        float y,
        float z,
        float yaw,
        float pitch,
        float limbSwing,
        EchoAnimState animationState
) {
}
