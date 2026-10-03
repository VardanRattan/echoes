package com.vardanrattan.echoes.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Equipment snapshot for ghost rendering accuracy.
 *
 * Stores full ItemStack data (components, count, damage) for each slot,
 * enabling accurate rendering of enchantments, custom names, and durability.
 *
 * Backward compatible with the legacy string-based format.
 */
public final class EquipmentSnapshot {

    private static final String TAG_VERSION = "v";
    private static final String TAG_ITEMS = "items";
    private static final int CURRENT_VERSION = 2;

    private static final int SLOT_MAIN_HAND = 0;
    private static final int SLOT_OFF_HAND = 1;
    private static final int SLOT_HEAD = 2;
    private static final int SLOT_CHEST = 3;
    private static final int SLOT_LEGS = 4;
    private static final int SLOT_FEET = 5;
    private static final int SLOT_COUNT = 6;

    private final ItemStack[] stacks;

    private EquipmentSnapshot(ItemStack[] stacks) {
        this.stacks = stacks;
    }

    public static EquipmentSnapshot of(ItemStack mainHand, ItemStack offHand, ItemStack head, ItemStack chest, ItemStack legs, ItemStack feet) {
        ItemStack[] stacks = new ItemStack[SLOT_COUNT];
        stacks[SLOT_MAIN_HAND] = mainHand == null ? ItemStack.EMPTY : mainHand;
        stacks[SLOT_OFF_HAND] = offHand == null ? ItemStack.EMPTY : offHand;
        stacks[SLOT_HEAD] = head == null ? ItemStack.EMPTY : head;
        stacks[SLOT_CHEST] = chest == null ? ItemStack.EMPTY : chest;
        stacks[SLOT_LEGS] = legs == null ? ItemStack.EMPTY : legs;
        stacks[SLOT_FEET] = feet == null ? ItemStack.EMPTY : feet;
        return new EquipmentSnapshot(stacks);
    }

    public static EquipmentSnapshot capture(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        ItemStack[] stacks = new ItemStack[SLOT_COUNT];
        stacks[SLOT_MAIN_HAND] = player.getMainHandItem().copy();
        stacks[SLOT_OFF_HAND] = player.getOffhandItem().copy();
        stacks[SLOT_HEAD] = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        stacks[SLOT_CHEST] = player.getItemBySlot(EquipmentSlot.CHEST).copy();
        stacks[SLOT_LEGS] = player.getItemBySlot(EquipmentSlot.LEGS).copy();
        stacks[SLOT_FEET] = player.getItemBySlot(EquipmentSlot.FEET).copy();
        return new EquipmentSnapshot(stacks);
    }

    public static EquipmentSnapshot fromNbt(CompoundTag nbt) {
        if (nbt == null) {
            return null;
        }

        int version = nbt.getInt(TAG_VERSION).orElse(0);
        if (version >= CURRENT_VERSION) {
            return fromNbtV2(nbt);
        }
        return fromLegacyNbt(nbt);
    }

    private static EquipmentSnapshot fromNbtV2(CompoundTag nbt) {
        ListTag items = nbt.getList(TAG_ITEMS).orElse(new ListTag());
        ItemStack[] stacks = new ItemStack[SLOT_COUNT];
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (i < items.size() && items.get(i) instanceof CompoundTag slotNbt) {
                var result = ItemStack.CODEC.parse(NbtOps.INSTANCE, slotNbt).result();
                stacks[i] = result.isPresent() ? result.get() : ItemStack.EMPTY;
            } else {
                stacks[i] = ItemStack.EMPTY;
            }
        }
        return new EquipmentSnapshot(stacks);
    }

    private static EquipmentSnapshot fromLegacyNbt(CompoundTag nbt) {
        ItemStack[] stacks = new ItemStack[SLOT_COUNT];
        stacks[SLOT_MAIN_HAND] = parseLegacyItem(nbt.getString("mainHand").orElse(""));
        stacks[SLOT_OFF_HAND] = parseLegacyItem(nbt.getString("offHand").orElse(""));
        stacks[SLOT_HEAD] = parseLegacyItem(nbt.getString("head").orElse(""));
        stacks[SLOT_CHEST] = parseLegacyItem(nbt.getString("chest").orElse(""));
        stacks[SLOT_LEGS] = parseLegacyItem(nbt.getString("legs").orElse(""));
        stacks[SLOT_FEET] = parseLegacyItem(nbt.getString("feet").orElse(""));
        return new EquipmentSnapshot(stacks);
    }

    private static ItemStack parseLegacyItem(String idStr) {
        if (idStr == null || idStr.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Identifier id = Identifier.tryParse(idStr);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        var item = BuiltInRegistries.ITEM.get(id);
        if (item.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item.get());
    }

    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt(TAG_VERSION, CURRENT_VERSION);

        ListTag items = new ListTag();
        for (int i = 0; i < SLOT_COUNT; i++) {
            CompoundTag slotNbt = new CompoundTag();
            if (stacks[i] != null && !stacks[i].isEmpty()) {
                var result = ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stacks[i]).result();
                if (result.isPresent() && result.get() instanceof CompoundTag cn) {
                    slotNbt.merge(cn);
                }
            }
            items.add(slotNbt);
        }
        nbt.put(TAG_ITEMS, items);
        return nbt;
    }

    public ItemStack getStack(Slot slot) {
        return stacks[slot.ordinal()] == null ? ItemStack.EMPTY : stacks[slot.ordinal()];
    }

    public String getItemId(Slot slot) {
        ItemStack stack = getStack(slot);
        if (stack.isEmpty()) {
            return "";
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    public String getMainHandItemId() { return getItemId(Slot.MAIN_HAND); }
    public String getOffHandItemId() { return getItemId(Slot.OFF_HAND); }
    public String getHeadItemId() { return getItemId(Slot.HEAD); }
    public String getChestItemId() { return getItemId(Slot.CHEST); }
    public String getLegsItemId() { return getItemId(Slot.LEGS); }
    public String getFeetItemId() { return getItemId(Slot.FEET); }

    public enum Slot {
        MAIN_HAND, OFF_HAND, HEAD, CHEST, LEGS, FEET
    }
}
