package com.vardanrattan.echoes.events;

import com.vardanrattan.echoes.config.EchoesConfig;
import com.vardanrattan.echoes.data.EchoEventType;
import com.vardanrattan.echoes.data.EchoFrame;
import com.vardanrattan.echoes.data.EchoWorldState;
import com.vardanrattan.echoes.data.EquipmentSnapshot;
import com.vardanrattan.echoes.data.PlayerEchoData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Handles TAMING (E2) and MAJOR_CRAFT (E3) triggers.
 */
public final class MilestoneEchoHandler {

    private static final Set<Item> MILESTONE_ITEMS = Set.of(
            Items.DIAMOND_PICKAXE,
            Items.DIAMOND_SWORD,
            Items.NETHERITE_INGOT,
            Items.ENCHANTING_TABLE,
            Items.BEACON,
            Items.ELYTRA
    );

    private MilestoneEchoHandler() {
    }

    public static void onTame(ServerPlayer player, BlockPos pos) {
        if (!EchoesConfig.get().isEnabled() || !EchoesConfig.get().isTamingEnabled()) {
            return;
        }
        emitMilestoneEcho(player, pos, EchoEventType.TAMING, "milestone:taming");
    }

    public static void onCraft(ServerPlayer player, ItemStack stack) {
        if (!EchoesConfig.get().isEnabled() || !EchoesConfig.get().isMajorCraftEnabled()) {
            return;
        }

        Item item = stack.getItem();
        if (MILESTONE_ITEMS.contains(item)) {
            String milestoneKey = "milestone:craft:" + item.toString();
            emitMilestoneEcho(player, player.blockPosition(), EchoEventType.MAJOR_CRAFT, milestoneKey);
        }
    }

    private static void emitMilestoneEcho(ServerPlayer player, BlockPos pos, EchoEventType type, String milestoneKey) {
        ServerLevel world = player.level();
        EchoWorldState state = EchoWorldState.get(world);
        PlayerEchoData data = state.getOrCreatePlayerData(player.getUUID());

        if (!data.getCraftedMilestones().contains(milestoneKey)) {
            data.addCraftedMilestone(milestoneKey);
            state.setDirty();

            List<EchoFrame> frames = singleFrame(world, player);
            if (!frames.isEmpty()) {
                var equipment = EquipmentSnapshot.capture(player);
                var record = EchoService.createEchoFromFrames(
                        world,
                        player,
                        type,
                        pos,
                        frames,
                        equipment
                );
                state.addEcho(record);
                EchoService.onEchoCreated(record);
                state.setDirty();
            }
        }
    }

    private static List<EchoFrame> singleFrame(ServerLevel world, ServerPlayer player) {
        var buffered = FrameSampler.captureBufferedFrame(player);
        if (buffered == null) return List.of();
        BlockPos anchor = player.blockPosition();
        EchoFrame frame = FrameSampler.toRelativeEchoFrame(buffered, anchor, 0);
        if (frame == null) return List.of();
        List<EchoFrame> frames = new ArrayList<>(1);
        frames.add(frame);
        return frames;
    }
}
