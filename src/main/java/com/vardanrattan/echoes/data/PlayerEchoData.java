package com.vardanrattan.echoes.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player tracking data used for echo triggers and privacy controls.
 */
public final class PlayerEchoData {

    private final Set<ResourceKey<Biome>> discoveredBiomes = new HashSet<>();
    private final Set<VisitedStructure> discoveredStructures = new HashSet<>();
    private final Set<ResourceKey<Level>> visitedDimensions = new HashSet<>();
    private final Set<String> craftedMilestones = new HashSet<>();
    private final Set<UUID> seenEchos = new HashSet<>();

    private boolean optedOut;
    private boolean displayOptedOut;

    private BlockPos sessionOrigin;
    private float sessionDistanceTraveled;

    public Set<ResourceKey<Biome>> getDiscoveredBiomes() {
        return Collections.unmodifiableSet(discoveredBiomes);
    }

    public void addDiscoveredBiome(ResourceKey<Biome> biomeKey) {
        if (biomeKey != null) {
            discoveredBiomes.add(biomeKey);
        }
    }

    public Set<VisitedStructure> getDiscoveredStructures() {
        return Collections.unmodifiableSet(discoveredStructures);
    }

    public void addDiscoveredStructure(VisitedStructure structure) {
        if (structure != null) {
            discoveredStructures.add(structure);
        }
    }

    public Set<ResourceKey<Level>> getVisitedDimensions() {
        return Collections.unmodifiableSet(visitedDimensions);
    }

    public void addVisitedDimension(ResourceKey<Level> dimension) {
        if (dimension != null) {
            visitedDimensions.add(dimension);
        }
    }

    public Set<String> getCraftedMilestones() {
        return Collections.unmodifiableSet(craftedMilestones);
    }

    public void addCraftedMilestone(String itemId) {
        if (itemId != null && !itemId.isEmpty()) {
            craftedMilestones.add(itemId);
        }
    }

    public Set<UUID> getSeenEchos() {
        return Collections.unmodifiableSet(seenEchos);
    }

    public void markEchoSeen(UUID echoUuid) {
        if (echoUuid != null) {
            seenEchos.add(echoUuid);
        }
    }

    public boolean hasSeenEcho(UUID echoUuid) {
        return echoUuid != null && seenEchos.contains(echoUuid);
    }

    public boolean isOptedOut() {
        return optedOut;
    }

    public void setOptedOut(boolean optedOut) {
        this.optedOut = optedOut;
    }

    public boolean isDisplayOptedOut() {
        return displayOptedOut;
    }

    public void setDisplayOptedOut(boolean displayOptedOut) {
        this.displayOptedOut = displayOptedOut;
    }

    public BlockPos getSessionOrigin() {
        return sessionOrigin;
    }

    public void setSessionOrigin(BlockPos sessionOrigin) {
        this.sessionOrigin = sessionOrigin == null ? null : sessionOrigin.immutable();
    }

    public float getSessionDistanceTraveled() {
        return sessionDistanceTraveled;
    }

    public void setSessionDistanceTraveled(float sessionDistanceTraveled) {
        this.sessionDistanceTraveled = sessionDistanceTraveled;
    }

    public void addToSessionDistanceTraveled(float delta) {
        if (delta > 0.0f) {
            this.sessionDistanceTraveled += delta;
        }
    }

    public void resetSessionDistance() {
        this.sessionDistanceTraveled = 0.0f;
    }
}

