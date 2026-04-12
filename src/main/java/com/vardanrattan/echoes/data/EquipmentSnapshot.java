package com.vardanrattan.echoes.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Minimal equipment snapshot for ghost rendering accuracy.
 *
 * Stored as item identifiers for MVP.
 *
 * Later we can upgrade this to full ItemStack component/NBT capture if needed.
 */
public final class EquipmentSnapshot {

    private final String mainHandItemId;
    private final String offHandItemId;
    private final String headItemId;
    private final String chestItemId;
    private final String legsItemId;
    private final String feetItemId;

    public EquipmentSnapshot(
            String mainHandItemId,
            String offHandItemId,
            String headItemId,
            String chestItemId,
            String legsItemId,
            String feetItemId) {
        this.mainHandItemId = mainHandItemId;
        this.offHandItemId = offHandItemId;
        this.headItemId = headItemId;
        this.chestItemId = chestItemId;
        this.legsItemId = legsItemId;
        this.feetItemId = feetItemId;
    }

    public static EquipmentSnapshot capture(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        String main = itemId(player.getMainHandItem());
        String off = itemId(player.getOffhandItem());
        String head = itemId(player.getItemBySlot(EquipmentSlot.HEAD));
        String chest = itemId(player.getItemBySlot(EquipmentSlot.CHEST));
        String legs = itemId(player.getItemBySlot(EquipmentSlot.LEGS));
        String feet = itemId(player.getItemBySlot(EquipmentSlot.FEET));
        return new EquipmentSnapshot(
                main, off, head, chest, legs, feet);
    }

    public static EquipmentSnapshot fromNbt(CompoundTag nbt) {
        if (nbt == null) {
            return null;
        }
        return new EquipmentSnapshot(
                nbt.getString("mainHand").orElse(""),
                nbt.getString("offHand").orElse(""),
                nbt.getString("head").orElse(""),
                nbt.getString("chest").orElse(""),
                nbt.getString("legs").orElse(""),
                nbt.getString("feet").orElse(""));
    }

    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("mainHand", mainHandItemId == null ? "" : mainHandItemId);
        nbt.putString("offHand", offHandItemId == null ? "" : offHandItemId);
        nbt.putString("head", headItemId == null ? "" : headItemId);
        nbt.putString("chest", chestItemId == null ? "" : chestItemId);
        nbt.putString("legs", legsItemId == null ? "" : legsItemId);
        nbt.putString("feet", feetItemId == null ? "" : feetItemId);
        return nbt;
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }
}
