// package com.vardanrattan.echoes.data;

// import org.junit.jupiter.api.Test;

// import static org.junit.jupiter.api.Assertions.*;

// /**
//  * Unit tests for {@link EchoFrame} — specifically the clamping/normalisation
//  * applied in the constructor and the {@link EchoFrame#toString()} helper.
//  *
//  * No Minecraft bootstrap is required; {@code EchoFrame} is pure Java.
//  */
// public class EchoFrameTest {

//     /** Helper: build a frame with given floats plus harmless defaults. */
//     private EchoFrame frame(float relX, float relY, float relZ,
//             float yaw, float pitch,
//             float limbSwing, int tick) {
//         return new EchoFrame(relX, relY, relZ, yaw, pitch,
//                 limbSwing, EchoAnimState.IDLE, tick);
//     }

//     // -------------------------------------------------------------------------
//     // relX / relY / relZ — clamped to ±512
//     // -------------------------------------------------------------------------

//     @Test
//     void relXClampedAtPositiveLimit() {
//         EchoFrame f = frame(9999f, 0f, 0f, 0f, 0f, 0f, 0);
//         assertEquals(512f, f.getRelX(), 0.001f);
//     }

//     @Test
//     void relXClampedAtNegativeLimit() {
//         EchoFrame f = frame(-9999f, 0f, 0f, 0f, 0f, 0f, 0);
//         assertEquals(-512f, f.getRelX(), 0.001f);
//     }

//     @Test
//     void relYClampedAtPositiveLimit() {
//         EchoFrame f = frame(0f, 1000f, 0f, 0f, 0f, 0f, 0);
//         assertEquals(512f, f.getRelY(), 0.001f);
//     }

//     @Test
//     void relZClampedAtNegativeLimit() {
//         EchoFrame f = frame(0f, 0f, -600f, 0f, 0f, 0f, 0);
//         assertEquals(-512f, f.getRelZ(), 0.001f);
//     }

//     @Test
//     void relCoordsWithinRangePassThrough() {
//         EchoFrame f = frame(10f, -5f, 200f, 0f, 0f, 0f, 0);
//         assertEquals(10f, f.getRelX(), 0.001f);
//         assertEquals(-5f, f.getRelY(), 0.001f);
//         assertEquals(200f, f.getRelZ(), 0.001f);
//     }

//     // -------------------------------------------------------------------------
//     // pitch — clamped to [-90, 90]
//     // -------------------------------------------------------------------------

//     @Test
//     void pitchClampedAtPositiveLimit() {
//         EchoFrame f = frame(0f, 0f, 0f, 0f, 200f, 0f, 0);
//         assertEquals(90f, f.getPitch(), 0.001f);
//     }

//     @Test
//     void pitchClampedAtNegativeLimit() {
//         EchoFrame f = frame(0f, 0f, 0f, 0f, -200f, 0f, 0);
//         assertEquals(-90f, f.getPitch(), 0.001f);
//     }

//     @Test
//     void pitchWithinRangePassesThrough() {
//         EchoFrame f = frame(0f, 0f, 0f, 0f, 45f, 0f, 0);
//         assertEquals(45f, f.getPitch(), 0.001f);
//     }

//     // -------------------------------------------------------------------------
//     // yaw — wrapped via %360
//     // -------------------------------------------------------------------------

//     @Test
//     void yawLargePositiveIsWrapped() {
//         EchoFrame f = frame(0f, 0f, 0f, 400f, 0f, 0f, 0);
//         assertEquals(400f % 360f, f.getYaw(), 0.001f);
//     }

//     @Test
//     void yawNegativeIsPreservedAsModulo() {
//         EchoFrame f = frame(0f, 0f, 0f, -10f, 0f, 0f, 0);
//         // Java's % preserves sign: -10 % 360 == -10
//         assertEquals(-10f % 360f, f.getYaw(), 0.001f);
//     }

//     @Test
//     void yawWithinRangePassesThrough() {
//         EchoFrame f = frame(0f, 0f, 0f, 180f, 0f, 0f, 0);
//         assertEquals(180f, f.getYaw(), 0.001f);
//     }

//     // -------------------------------------------------------------------------
//     // tickOffset — must be non-negative
//     // -------------------------------------------------------------------------

//     @Test
//     void negativeTickOffsetClampedToZero() {
//         EchoFrame f = frame(0f, 0f, 0f, 0f, 0f, 0f, -5);
//         assertEquals(0, f.getTickOffset());
//     }

//     @Test
//     void positiveTickOffsetPassesThrough() {
//         EchoFrame f = frame(0f, 0f, 0f, 0f, 0f, 0f, 42);
//         assertEquals(42, f.getTickOffset());
//     }

//     // -------------------------------------------------------------------------
//     // toString
//     // -------------------------------------------------------------------------

//     @Test
//     void toStringContainsTickOffset() {
//         EchoFrame f = frame(1f, 2f, 3f, 90f, 30f, 0f, 7);
//         String s = f.toString();
//         assertTrue(s.contains("7"), "toString should contain tickOffset value");
//     }

//     @Test
//     void toStringContainsAnimState() {
//         EchoFrame f = frame(0f, 0f, 0f, 0f, 0f, 0f, 0);
//         assertTrue(f.toString().contains("IDLE"),
//                 "toString should contain the animationState name");
//     }
// }
