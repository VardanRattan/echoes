package com.vardanrattan.echoes;

import com.vardanrattan.echoes.data.EchoAnimState;
import com.vardanrattan.echoes.data.EchoEventType;
import com.vardanrattan.echoes.data.EchoFrame;
import com.vardanrattan.echoes.data.EchoTier;
import com.vardanrattan.echoes.data.EquipmentSnapshot;
import com.vardanrattan.echoes.network.EchoClientNetworking;
import com.vardanrattan.echoes.network.EchoNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EchoesClient implements ClientModInitializer {

    private static int photoTickCounter = 0;
    private static boolean ghostSpawned = false;
    private static boolean originalHideGui = false;

    @Override
    public void onInitializeClient() {
        // Initialize client-side networking receivers and level render handlers.
        EchoNetworking.initClient();
        EchoClientNetworking.init();

        // Automated screenshot capture runner if property or marker file exists
        if (Boolean.getBoolean("echoes.screenshot") || java.nio.file.Files.exists(java.nio.file.Path.of("echoes_screenshot.txt"))) {
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (client.screen instanceof net.minecraft.client.gui.screens.TitleScreen && photoTickCounter == 0) {
                    photoTickCounter = 1;
                    client.execute(() -> {
                        client.createWorldOpenFlows().openWorld("New World", () -> {});
                    });
                    return;
                }

                if (client.level == null || client.player == null) {
                    return;
                }

                photoTickCounter++;

                // Dismiss any pause menu throughout automated capture
                client.options.pauseOnLostFocus = false;
                if (client.screen != null) {
                    client.setScreen(null);
                }

                // Wait 40 ticks for world rendering and lighting to stabilize
                // Initialize camera and GUI at tick 40
                if (photoTickCounter == 40 && !ghostSpawned) {
                    ghostSpawned = true;
                    originalHideGui = client.options.hideGui;
                    client.options.hideGui = true;
                    client.setScreen(null);

                    Player player = client.player;
                    player.setXRot(-5.0f);
                    player.xRotO = -5.0f;
                }

                Player player = client.player;
                if (player == null) return;

                double rad = Math.toRadians(player.getYRot());
                double dx = -Math.sin(rad) * 3.5;
                double dz = Math.cos(rad) * 3.5;
                Vec3 ghostPos = new Vec3(player.getX() + dx, player.getY(), player.getZ() + dz);
                BlockPos anchor = BlockPos.containing(ghostPos);
                float facePlayerYaw = (float) (player.getYRot() + 180.0f);

                // PHASE 1 (Tick 42): Journey Echo (Whisper tier, walking in the wild)
                if (photoTickCounter == 42) {
                    EchoClientNetworking.clearActiveGhosts();
                    List<EchoFrame> frames = new ArrayList<>();
                    for (int i = 0; i < 40; i++) {
                        float swing = (float) Math.sin(i * 0.25) * 0.6f;
                        frames.add(new EchoFrame(0f, 0f, 0f, facePlayerYaw, 0f, swing, EchoAnimState.IDLE, i));
                    }
                    EquipmentSnapshot equip = EquipmentSnapshot.of(
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD),
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TORCH),
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET),
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE),
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_LEGGINGS),
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_BOOTS)
                    );
                    EchoClientNetworking.spawnLocalGhost(
                            UUID.randomUUID(), frames, EchoTier.WHISPER, EchoEventType.JOURNEY_LONG,
                            anchor, equip, player.getUUID(), player.getScoreboardName(), System.currentTimeMillis() - 7200000L
                    );
                }
                if (photoTickCounter == 62) {
                    client.setScreen(null);
                    captureScreenshot(client, "echoes_showcase_1.png");
                }

                // PHASE 2 (Tick 70): World First / Boss Triumph (World First tier, Netherite & Diamond)
                if (photoTickCounter == 70) {
                    EchoClientNetworking.clearActiveGhosts();
                    List<EchoFrame> frames = new ArrayList<>();
                    for (int i = 0; i < 40; i++) {
                        frames.add(new EchoFrame(0f, 0f, 0f, facePlayerYaw, -15f, 0f, EchoAnimState.IDLE, i));
                    }
                    EquipmentSnapshot equip = EquipmentSnapshot.of(
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD),
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD),
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_HELMET),
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE),
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_LEGGINGS),
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_BOOTS)
                    );
                    EchoClientNetworking.spawnLocalGhost(
                            UUID.randomUUID(), frames, EchoTier.WORLD_FIRST, EchoEventType.BOSS_KILL,
                            anchor, equip, player.getUUID(), player.getScoreboardName(), System.currentTimeMillis() - 14400000L
                    );
                }
                if (photoTickCounter == 90) {
                    client.setScreen(null);
                    captureScreenshot(client, "echoes_showcase_2.png");
                }

                // PHASE 3 (Tick 98): Tragic Death Echo (Mark tier, dying collapse)
                if (photoTickCounter == 98) {
                    EchoClientNetworking.clearActiveGhosts();
                    List<EchoFrame> frames = new ArrayList<>();
                    for (int i = 0; i < 40; i++) {
                        frames.add(new EchoFrame(0f, 0f, 0f, facePlayerYaw, 0f, 0f, EchoAnimState.DYING, i));
                    }
                    EquipmentSnapshot equip = EquipmentSnapshot.of(
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE),
                            net.minecraft.world.item.ItemStack.EMPTY,
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET),
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE),
                            net.minecraft.world.item.ItemStack.EMPTY,
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_BOOTS)
                    );
                    EchoClientNetworking.spawnLocalGhost(
                            UUID.randomUUID(), frames, EchoTier.MARK, EchoEventType.DEATH,
                            anchor, equip, player.getUUID(), player.getScoreboardName(), System.currentTimeMillis() - 3600000L
                    );
                }
                if (photoTickCounter == 118) {
                    client.setScreen(null);
                    captureScreenshot(client, "echoes_showcase_3.png");
                }

                // PHASE 4 (Tick 126): Echo Crystal & Sneak Memory (Scar tier, crouching / inspecting)
                if (photoTickCounter == 126) {
                    EchoClientNetworking.clearActiveGhosts();
                    List<EchoFrame> frames = new ArrayList<>();
                    for (int i = 0; i < 40; i++) {
                        frames.add(new EchoFrame(0f, 0f, 0f, facePlayerYaw, 25f, 0f, EchoAnimState.CROUCHING, i));
                    }
                    EquipmentSnapshot equip = EquipmentSnapshot.of(
                            new net.minecraft.world.item.ItemStack(com.vardanrattan.echoes.item.EchoItems.ECHO_CRYSTAL),
                            net.minecraft.world.item.ItemStack.EMPTY,
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET),
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE),
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_LEGGINGS),
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_BOOTS)
                    );
                    EchoClientNetworking.spawnLocalGhost(
                            UUID.randomUUID(), frames, EchoTier.SCAR, EchoEventType.MANUAL_CRYSTAL,
                            anchor, equip, player.getUUID(), player.getScoreboardName(), System.currentTimeMillis() - 86400000L
                    );
                }
                if (photoTickCounter == 146) {
                    client.setScreen(null);
                    captureScreenshot(client, "echoes_showcase_4.png");
                }

                // Complete and restore GUI at tick 165
                if (photoTickCounter == 165) {
                    client.options.hideGui = originalHideGui;
                    Echoes.LOGGER.info("[Echoes-AutoScreenshot] All 4 showcase screenshots completed! Closing client.");
                    client.stop();
                }
            });
        }
    }

    private static void captureScreenshot(Minecraft client, String targetFileName) {
        Screenshot.grab(
                client.gameDirectory,
                client.getMainRenderTarget(),
                msg -> {
                    try {
                        Path screenshotsDir = client.gameDirectory.toPath().resolve("screenshots");
                        Path assetsTarget = Path.of("assets/screenshots");
                        Files.createDirectories(assetsTarget);

                        // Find the most recently created screenshot
                        File[] files = screenshotsDir.toFile().listFiles((dir, name) -> name.endsWith(".png"));
                        if (files != null && files.length > 0) {
                            File latest = files[0];
                            for (File f : files) {
                                if (f.lastModified() > latest.lastModified()) {
                                    latest = f;
                                }
                            }
                            Path dest = assetsTarget.resolve(targetFileName);
                            Files.copy(latest.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
                            Echoes.LOGGER.info("[Echoes-AutoScreenshot] Saved screenshot to: {}", dest.toAbsolutePath());
                        }
                    } catch (IOException e) {
                        Echoes.LOGGER.error("[Echoes-AutoScreenshot] Failed to copy screenshot", e);
                    }
                }
        );
    }
}