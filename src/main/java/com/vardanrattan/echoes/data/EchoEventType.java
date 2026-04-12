package com.vardanrattan.echoes.data;

/**
 * High-level echo event types.
 * These align with the taxonomy in ECHOES_MOD_DEVDOC.md.
 */
public enum EchoEventType {
    // Core anchors
    DEATH,
    STRUCTURE_DISCOVERY,
    BIOME_DISCOVERY,
    DIMENSION_ENTER,
    BOSS_KILL,
    MAJOR_CRAFT,
    TAMING,

    // Journeys
    JOURNEY_LONG,      // Tier 1 long journey end
    JOURNEY_MARATHON,  // Tier 3 marathon journey

    // Manual / special
    MANUAL_CRYSTAL,
    WORLD_FIRST
}

