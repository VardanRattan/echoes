package com.vardanrattan.echoes.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.entity.EquipmentSlot;

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
            String feetItemId
    ) {
        this.mainHandItemId = mainHandItemId;
        this.offHandItemId = offHandItemId;
        this.headItemId = headItemId;
        this.chestItemId = chestItemId;
        this.legsItemId = legsItemId;
        this.feetItemId = feetItemId;
    }

    public static EquipmentSnapshot capture(ServerPlayerEntity player) {
        if (player == null) {
            return null;
        }
        String main = itemId(player.getMainHandStack());
        String off = itemId(player.getOffHandStack());
        String head = itemId(player.getEquippedStack(EquipmentSlot.HEAD));
        String chest = itemId(player.getEquippedStack(EquipmentSlot.CHEST));
        String legs = itemId(player.getEquippedStack(EquipmentSlot.LEGS));
        String feet = itemId(player.getEquippedStack(EquipmentSlot.FEET));
        return new EquipmentSnapshot(
                main, off, head, chest, legs, feet
        );
    }

    public static EquipmentSnapshot fromNbt(NbtCompound nbt) {
        if (nbt == null) {
            return null;
        }
        return new EquipmentSnapshot(
                nbt.getString("mainHand"),
                nbt.getString("offHand"),
                nbt.getString("head"),
                nbt.getString("chest"),
                nbt.getString("legs"),
                nbt.getString("feet")
        );
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("mainHand", mainHandItemId == null ? "" : mainHandItemId);
        nbt.putString("offHand", offHandItemId == null ? "" : offHandItemId);
        nbt.putString("head", headItemId == null ? "" : headItemId);
        nbt.putString("chest", chestItemId == null ? "" : chestItemId);
        nbt.putString("legs", legsItemId == null ? "" : legsItemId);
        nbt.putString("feet", feetItemId == null ? "" : feetItemId);
        return nbt;
    }

    private static String itemId(net.minecraft.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return id == null ? "" : id.toString();
    }
}

