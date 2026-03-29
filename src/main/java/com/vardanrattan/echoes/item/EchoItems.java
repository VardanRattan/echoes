package com.vardanrattan.echoes.item;

import com.vardanrattan.echoes.Echoes;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

/**
 * Item registration for Echoes.
 */
public final class EchoItems {

    public static final RegistryKey<Item> ECHO_CRYSTAL_KEY =
            RegistryKey.of(RegistryKeys.ITEM, Identifier.of(Echoes.MOD_ID, "echo_crystal"));

    public static final Item ECHO_CRYSTAL = new EchoCrystalItem(
            new Item.Settings()
                    .maxCount(1)
                    .maxDamage(16)
                    .rarity(Rarity.UNCOMMON)
    );

    private EchoItems() {
    }

    public static void register() {
        Registry.register(Registries.ITEM, Identifier.of(Echoes.MOD_ID, "echo_crystal"), ECHO_CRYSTAL);
    }
}

