package com.vardanrattan.echoes.item;

import com.vardanrattan.echoes.Echoes;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Rarity;

/**
 * Item registration for Echoes.
 */
public final class EchoItems {

    public static final ResourceKey<Item> ECHO_CRYSTAL_KEY =
            ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Echoes.MOD_ID, "echo_crystal"));

    public static final Item ECHO_CRYSTAL = new EchoCrystalItem(
            new Item.Properties()
                    .setId(ECHO_CRYSTAL_KEY)
                    .stacksTo(1)
                    .durability(16)
                    .rarity(Rarity.UNCOMMON)
    );
    
    private EchoItems() {
    }

    public static void register() {
    Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath(Echoes.MOD_ID, "echo_crystal"), ECHO_CRYSTAL);
}
}

