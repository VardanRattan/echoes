package com.vardanrattan.echoes.item;

import com.vardanrattan.echoes.Echoes;
import com.vardanrattan.echoes.config.EchoesConfig;
import com.vardanrattan.echoes.data.EchoRecord;
import com.vardanrattan.echoes.data.EchoWorldState;
import com.vardanrattan.echoes.network.EchoPrivacy;
import com.vardanrattan.echoes.network.EchoSensePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Echo Crystal:
 * - Right-click: sense nearby echoes (particle burst, sound).
 * - Sneak + right-click: manual echo recording (8s).
 */
public class EchoCrystalItem extends Item {

    private static final int SENSE_RADIUS = 32;
    private static final int SENSE_COOLDOWN_TICKS = 20 * 30; // 30s

    public EchoCrystalItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (!(user instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }

        ServerLevel serverWorld = (ServerLevel) world;

        EchoesConfig cfg = EchoesConfig.get();
        if (!cfg.isEnabled() || !cfg.isManualCrystalEnabled()) {
            return InteractionResult.FAIL;
        }

        if (player.isShiftKeyDown()) {
            // F1: Manual recording session starts here.
            stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);

            Echoes.getRecordingSessionManager().startManualRecording(player);
            player.sendSystemMessage(Component.translatable("item.echoes.echo_crystal.manual.started"), true);
            player.awardStat(Stats.ITEM_USED.get(this), 1);
        } else {
            // F5: Check if looking at an echo for metadata tooltip.
            if (tryShowMetadataTooltip(serverWorld, player)) {
                return InteractionResult.SUCCESS;
            }

            // Otherwise, normal pulse effect.
            senseNearbyEchoes(serverWorld, player);
player.getCooldowns().addCooldown(stack, SENSE_COOLDOWN_TICKS);
            player.awardStat(Stats.ITEM_USED.get(this), 1);
        }

        return InteractionResult.SUCCESS;
    }

    private boolean tryShowMetadataTooltip(ServerLevel world, ServerPlayer player) {
        Vec3 start = player.getEyePosition();
        Vec3 dir = player.getLookAngle();
        double range = 10.0;
        Vec3 end = start.add(dir.scale(range));

        EchoWorldState state = EchoWorldState.get(world);
        var nearby = state.getEchoesNear(player.blockPosition(), 16, world.dimension());

        EchoRecord hitEcho = null;
        double minDistance = Double.MAX_VALUE;

        for (var echo : nearby) {
            // Check if player is allowed to see it (F6 audit point)
            if (!EchoPrivacy.canPlayerSeeEcho(player.getUUID(), echo)) continue;

            // Simplified collision check: treat the echo as a 0.6x1.8 box at its anchor.
            // In a real mod, we'd use the frames to find the actual position at this world time.
            BlockPos anchor = echo.getAnchorPos();
            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                    anchor.getX() + 0.2, anchor.getY(), anchor.getZ() + 0.2,
                    anchor.getX() + 0.8, anchor.getY() + 1.8, anchor.getZ() + 0.8
            );

            java.util.Optional<Vec3> hit = box.clip(start, end);
            if (hit.isPresent()) {
                double dist = start.distanceToSqr(hit.get());
                if (dist < minDistance) {
                    minDistance = dist;
                    hitEcho = echo;
                }
            }
        }

        if (hitEcho != null) {
            // F5: player name, relative time, event type
            long ageMs = System.currentTimeMillis() - hitEcho.getRealTimestamp();
            String relativeTime = formatRelativeTime(ageMs);

            // F6: Use EchoPrivacy for name resolution
            String displayName = EchoPrivacy.resolvePlayerName(hitEcho);

            player.sendSystemMessage(Component.empty()
                    .append(Component.literal("Echo: ").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(displayName).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" (").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(hitEcho.getEventType().name()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(")").withStyle(ChatFormatting.GRAY)), false);

            player.sendSystemMessage(Component.empty()
                    .append(Component.literal("Recorded: ").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(relativeTime).withStyle(ChatFormatting.WHITE)), false);
            
            return true;
        }

        return false;
    }

    private String formatRelativeTime(long ageMs) {
        long seconds = TimeUnit.MILLISECONDS.toSeconds(ageMs);
        if (seconds < 60) return seconds + "s ago";
        long minutes = TimeUnit.MILLISECONDS.toMinutes(ageMs);
        if (minutes < 60) return minutes + "m ago";
        long hours = TimeUnit.MILLISECONDS.toHours(ageMs);
        if (hours < 24) return hours + "h ago";
        long days = TimeUnit.MILLISECONDS.toDays(ageMs);
        return days + "d ago";
    }

    private void senseNearbyEchoes(ServerLevel world, ServerPlayer player) {
        EchoWorldState state = EchoWorldState.get(world);
        BlockPos center = player.blockPosition();

        var echoes = state.getEchoesNear(center, SENSE_RADIUS, world.dimension());
        if (echoes.isEmpty()) {
            // Subtle feedback when nothing is found.
            world.playSound(
                    null,
                    center,
                    SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS,
                    0.3f,
                    0.6f
            );
            return;
        }

        // Server → client sense (crystal right-click)
        List<EchoSensePayload.SenseEntry> entries = new ArrayList<>(echoes.size());
        for (var echo : echoes) {
            String resolvedName = EchoPrivacy.resolvePlayerName(echo);
            entries.add(new EchoSensePayload.SenseEntry(
                    echo.getUUID(),
                    echo.getAnchorPos(),
                    echo.getTier(),
                    echo.getEventType(),
                    resolvedName == null ? "" : resolvedName,
                    echo.getRealTimestamp()
            ));
        }

        ServerPlayNetworking.send(player, new EchoSensePayload(entries));

        world.playSound(
                null,
                center,
                SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS,
                0.7f,
                1.2f
        );
    }
}

