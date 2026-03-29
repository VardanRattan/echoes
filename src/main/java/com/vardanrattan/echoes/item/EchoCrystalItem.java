package com.vardanrattan.echoes.item;

import com.vardanrattan.echoes.Echoes;
import com.vardanrattan.echoes.config.EchoesConfig;
import com.vardanrattan.echoes.data.EchoRecord;
import com.vardanrattan.echoes.data.EchoWorldState;
import com.vardanrattan.echoes.network.EchoPrivacy;
import com.vardanrattan.echoes.network.EchoSensePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

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

    public EchoCrystalItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.success(stack);
        }

        ServerWorld serverWorld = (ServerWorld) world;

        EchoesConfig cfg = EchoesConfig.get();
        if (!cfg.isEnabled() || !cfg.isManualCrystalEnabled()) {
            return TypedActionResult.fail(stack);
        }

        if (player.isSneaking()) {
            // F1: Manual recording session starts here.
            stack.damage(1, serverWorld, player, (item) -> player.sendEquipmentBreakStatus(item, hand == Hand.MAIN_HAND ? net.minecraft.entity.EquipmentSlot.MAINHAND : net.minecraft.entity.EquipmentSlot.OFFHAND));

            Echoes.getRecordingSessionManager().startManualRecording(player);
            player.sendMessage(Text.translatable("item.echoes.echo_crystal.manual.started"), true);
            player.incrementStat(Stats.USED.getOrCreateStat(this));
        } else {
            // F5: Check if looking at an echo for metadata tooltip.
            if (tryShowMetadataTooltip(serverWorld, player)) {
                return TypedActionResult.success(stack);
            }

            // Otherwise, normal pulse effect.
            senseNearbyEchoes(serverWorld, player);
            player.getItemCooldownManager().set(this, SENSE_COOLDOWN_TICKS);
            player.incrementStat(Stats.USED.getOrCreateStat(this));
        }

        return TypedActionResult.success(stack);
    }

    private boolean tryShowMetadataTooltip(ServerWorld world, ServerPlayerEntity player) {
        Vec3d start = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0f);
        double range = 10.0;
        Vec3d end = start.add(dir.multiply(range));

        EchoWorldState state = EchoWorldState.get(world);
        var nearby = state.getEchoesNear(player.getBlockPos(), 16, world.getRegistryKey());

        EchoRecord hitEcho = null;
        double minDistance = Double.MAX_VALUE;

        for (var echo : nearby) {
            // Check if player is allowed to see it (F6 audit point)
            if (!EchoPrivacy.canPlayerSeeEcho(player.getUuid(), echo)) continue;

            // Simplified collision check: treat the echo as a 0.6x1.8 box at its anchor.
            // In a real mod, we'd use the frames to find the actual position at this world time.
            BlockPos anchor = echo.getAnchorPos();
            net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(
                    anchor.getX() + 0.2, anchor.getY(), anchor.getZ() + 0.2,
                    anchor.getX() + 0.8, anchor.getY() + 1.8, anchor.getZ() + 0.8
            );

            java.util.Optional<Vec3d> hit = box.raycast(start, end);
            if (hit.isPresent()) {
                double dist = start.squaredDistanceTo(hit.get());
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

            player.sendMessage(Text.empty()
                    .append(Text.literal("Echo: ").formatted(Formatting.GOLD))
                    .append(Text.literal(displayName).formatted(Formatting.WHITE))
                    .append(Text.literal(" (").formatted(Formatting.GRAY))
                    .append(Text.literal(hitEcho.getEventType().name()).formatted(Formatting.AQUA))
                    .append(Text.literal(")").formatted(Formatting.GRAY)), false);

            player.sendMessage(Text.empty()
                    .append(Text.literal("Recorded: ").formatted(Formatting.GOLD))
                    .append(Text.literal(relativeTime).formatted(Formatting.WHITE)), false);
            
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

    private void senseNearbyEchoes(ServerWorld world, ServerPlayerEntity player) {
        EchoWorldState state = EchoWorldState.get(world);
        BlockPos center = player.getBlockPos();

        var echoes = state.getEchoesNear(center, SENSE_RADIUS, world.getRegistryKey());
        if (echoes.isEmpty()) {
            // Subtle feedback when nothing is found.
            world.playSound(
                    null,
                    center,
                    SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                    SoundCategory.PLAYERS,
                    0.3f,
                    0.6f
            );
            return;
        }

        // Server → client sense (crystal right-click)
        List<EchoSensePayload.SenseEntry> entries = new ArrayList<>(echoes.size());
        for (var echo : echoes) {
            entries.add(new EchoSensePayload.SenseEntry(
                    echo.getUuid(),
                    echo.getAnchorPos(),
                    echo.getTier(),
                    echo.getEventType(),
                    echo.getPlayerName(),
                    echo.getRealTimestamp()
            ));
        }

        ServerPlayNetworking.send(player, new EchoSensePayload(entries));

        world.playSound(
                null,
                center,
                SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE,
                SoundCategory.PLAYERS,
                0.7f,
                1.2f
        );
    }
}

