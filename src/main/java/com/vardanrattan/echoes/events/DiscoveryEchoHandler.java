package com.vardanrattan.echoes.events;

import com.vardanrattan.echoes.config.EchoesConfig;
import com.vardanrattan.echoes.data.EchoEventType;
import com.vardanrattan.echoes.data.EchoFrame;
import com.vardanrattan.echoes.data.EchoWorldState;
import com.vardanrattan.echoes.data.PlayerEchoData;
import com.vardanrattan.echoes.data.EquipmentSnapshot;
import com.vardanrattan.echoes.data.VisitedStructure;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.Structure;

import java.util.List;
import java.util.Set;

/**
 * Handles biome discovery and dimension enter echo triggers.
 *
 * Structure discovery and other echoes will be layered in here later.
 */
public final class DiscoveryEchoHandler {

    private static final int SAMPLE_INTERVAL_TICKS = 20; // once per second

    private static final Set<String> MAJOR_STRUCTURES = Set.of(
            "minecraft:stronghold",
            "minecraft:woodland_mansion",
            "minecraft:ocean_monument",
            "minecraft:end_city",
            "minecraft:fortress",
            "minecraft:ancient_city",
            "minecraft:bastion_remnant",
            "minecraft:buried_treasure",
            "minecraft:jungle_pyramid",
            "minecraft:desert_pyramid",
            "minecraft:igloo",
            "minecraft:pillager_outpost",
            "minecraft:trial_chambers"
    );

    private DiscoveryEchoHandler() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(DiscoveryEchoHandler::onWorldTick);
    }

    private static void onWorldTick(ServerWorld world) {
        EchoesConfig cfg = EchoesConfig.get();
        if (!cfg.isEnabled()) {
            return;
        }

        long time = world.getTime();
        if (time % SAMPLE_INTERVAL_TICKS != 0) {
            return;
        }

        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player == null || player.isRemoved()) continue;

            EchoWorldState state = EchoWorldState.get(world);
            PlayerEchoData data = state.getOrCreatePlayerData(player.getUuid());
            if (data.isOptedOut()) continue;

            // Biome discovery
            if (cfg.isBiomeDiscoveryEnabled()) {
                handleBiomeDiscovery(world, player, state, data);
            }

            // Dimension enter (first time in a dimension)
            if (cfg.isDimensionEnterEnabled()) {
                handleDimensionVisit(world, player, state, data);
            }

            // Structure discovery
            if (cfg.isStructureDiscoveryEnabled()) {
                handleStructureDiscovery(world, player, state, data);
            }
        }
    }

    private static void handleBiomeDiscovery(ServerWorld world, ServerPlayerEntity player, EchoWorldState state, PlayerEchoData data) {
        RegistryEntry<Biome> biomeEntry = world.getBiome(player.getBlockPos());
        RegistryKey<Biome> biomeKey = biomeEntry.getKey().orElse(null);
        if (biomeKey == null) return;

        if (!data.getDiscoveredBiomes().contains(biomeKey)) {
            data.addDiscoveredBiome(biomeKey);
            state.markDirty();

            // Create a tiny echo where the player is standing.
            List<EchoFrame> frames = FrameSampler.singleFrame(world, player);
            if (!frames.isEmpty()) {
                var equipment = EquipmentSnapshot.capture(player);
                var record = EchoService.createEchoFromFrames(
                        world,
                        player,
                        EchoEventType.BIOME_DISCOVERY,
                        player.getBlockPos(),
                        frames,
                        equipment
                );
                state.addEcho(record);
                EchoService.onEchoCreated(record);
            }
        }
    }

    private static void handleDimensionVisit(ServerWorld world, ServerPlayerEntity player, EchoWorldState state, PlayerEchoData data) {
        var dimKey = world.getRegistryKey();
        if (!data.getVisitedDimensions().contains(dimKey)) {
            data.addVisitedDimension(dimKey);
            state.markDirty();

            List<EchoFrame> frames = FrameSampler.singleFrame(world, player);
            if (!frames.isEmpty()) {
                var equipment = EquipmentSnapshot.capture(player);
                var record = EchoService.createEchoFromFrames(
                        world,
                        player,
                        EchoEventType.DIMENSION_ENTER,
                        player.getBlockPos(),
                        frames,
                        equipment
                );
                state.addEcho(record);
                EchoService.onEchoCreated(record);
            }
        }
    }

    private static void handleStructureDiscovery(ServerWorld world, ServerPlayerEntity player, EchoWorldState state, PlayerEchoData data) {
        BlockPos pos = player.getBlockPos();
        var structureStart = world.getStructureAccessor().getStructureContaining(pos, entry -> true);
        
        if (structureStart != null && structureStart.hasChildren()) {
            Structure structure = structureStart.getStructure();
            Identifier structureId = world.getRegistryManager().get(RegistryKeys.STRUCTURE).getId(structure);
            if (structureId == null) return;

            String idStr = structureId.toString();
            if (MAJOR_STRUCTURES.contains(idStr)) {
                VisitedStructure vs = new VisitedStructure(structureId, pos);
                if (!data.getDiscoveredStructures().contains(vs)) {
                    data.addDiscoveredStructure(vs);
                    state.markDirty();

                    List<EchoFrame> frames = FrameSampler.singleFrame(world, player);
                    if (!frames.isEmpty()) {
                        var equipment = EquipmentSnapshot.capture(player);
                        var record = EchoService.createEchoFromFrames(
                                world,
                                player,
                                EchoEventType.STRUCTURE_DISCOVERY,
                                pos,
                                frames,
                                equipment
                        );
                        state.addEcho(record);
                        EchoService.onEchoCreated(record);
                    }
                }
            }
        }
    }
}

