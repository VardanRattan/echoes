package com.vardanrattan.echoes.config;

import com.vardanrattan.echoes.Echoes;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Simple TOML-like config loader for Echoes.
 *
 * This intentionally avoids a heavy TOML dependency. It supports the subset
 * of TOML we need: section headers ([section]) and "key = value" pairs with
 * primitive types. Unknown keys are ignored.
 *
 * Config is loaded once at startup into an immutable snapshot accessed via
 * {@link #get()}.
 */
public final class EchoesConfig {

    private static final String FILE_NAME = "echoes.toml";
    private static final int CURRENT_VERSION = 1;

    private static volatile EchoesConfig instance = new EchoesConfig();

    // meta
    private int configVersion = CURRENT_VERSION;

    // [general]
    private boolean enabled = true;
    private boolean selfEchoesVisible = true;
    private boolean allowPlayerOptout = true;

    // [triggers]
    private boolean deathEnabled = true;
    private boolean structureDiscoveryEnabled = true;
    private boolean bossKillEnabled = true;
    private boolean dimensionEnterEnabled = true;
    private boolean majorCraftEnabled = true;
    private boolean biomeDiscoveryEnabled = true;
    private boolean tamingEnabled = true;
    private boolean worldFirstEnabled = true;
    private boolean manualCrystalEnabled = true;

    private int journeyTier1Distance = 500;
    private int journeyTier2Distance = 2000;

    // [decay]
    private boolean decayEnabled = true;
    private int whisperDays = 7;
    private int markDays = 30;
    private int scarDays = 60;
    private int worldFirstDays = -1; // -1 = never

    // [limits]
    private int maxEchoesPerChunk = 8;
    private int maxEchoesPerPlayer = 50;
    private int maxEchoesGlobal = 2000;

    // [playback]
    private int triggerRadius = 16;
    private int maxConcurrentPerPlayer = 1;
    private float whisperOpacity = 0.25f;
    private float markOpacity = 0.45f;
    private float scarOpacity = 0.70f;

    // [performance]
    private int scanIntervalTicks = 10;
    private boolean asyncRecording = true;

    // [privacy]
    private boolean hidePlayerNames = false;
    private boolean anonymizeAll = false;

    // Package-private so EchoesConfigTest can instantiate without reflection.
    EchoesConfig() {
    }

    public static EchoesConfig get() {
        return instance;
    }

    /**
     * Load config from disk or create a default one if missing.
     * Should be called once during mod initialization.
     */
    public static void load() {
        var loader = FabricLoader.getInstance();
        if (loader == null) {
            // Probably a unit test.
            instance = new EchoesConfig();
            return;
        }

        Path configPath = loader.getConfigDir().resolve(FILE_NAME);
        EchoesConfig config = new EchoesConfig();

        if (Files.exists(configPath)) {
            try {
                List<String> lines = Files.readAllLines(configPath, StandardCharsets.UTF_8);
                parseInto(config, lines);
            } catch (IOException e) {
                Echoes.LOGGER.warn("Failed to read Echoes config, using defaults", e);
            }
        } else {
            try {
                Files.createDirectories(configPath.getParent());
                Files.writeString(configPath, defaultToml(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                Echoes.LOGGER.warn("Failed to write default Echoes config", e);
            }
        }

        // Migration: if the on-disk version is older, log a warning and reset
        // the version field. Per-version migration logic can be added here as
        // NEW_FIELD = default_value blocks before the version bump.
        if (config.configVersion < CURRENT_VERSION) {
            Echoes.LOGGER.warn(
                    "Echoes config version {} is older than current {}; applying defaults for new fields.",
                    config.configVersion, CURRENT_VERSION);
            // Future per-version migration logic here.
            config.configVersion = CURRENT_VERSION;
        }

        instance = config;
        Echoes.LOGGER.info("Echoes config loaded (enabled={}, decayEnabled={}, triggerRadius={})",
                config.enabled, config.decayEnabled, config.triggerRadius);
    }

    static void parseInto(EchoesConfig config, List<String> lines) {
        String currentSection = "";
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                currentSection = line.substring(1, line.length() - 1).trim();
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            // Strip quotes if present
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            }
            String fullKey = currentSection.isEmpty() ? key : currentSection + "." + key;
            applyValue(config, fullKey, value);
        }
    }

    private static void applyValue(EchoesConfig c, String key, String rawValue) {
        String v = rawValue.trim();
        try {
            switch (key) {
                case "meta.config-version" -> c.configVersion = parseInt(v, c.configVersion);

                case "general.enabled" -> c.enabled = parseBoolean(v, c.enabled);
                case "general.self-echoes-visible" -> c.selfEchoesVisible = parseBoolean(v, c.selfEchoesVisible);
                case "general.allow-player-optout" -> c.allowPlayerOptout = parseBoolean(v, c.allowPlayerOptout);

                case "triggers.death" -> c.deathEnabled = parseBoolean(v, c.deathEnabled);
                case "triggers.structure-discovery" ->
                    c.structureDiscoveryEnabled = parseBoolean(v, c.structureDiscoveryEnabled);
                case "triggers.boss-kill" -> c.bossKillEnabled = parseBoolean(v, c.bossKillEnabled);
                case "triggers.dimension-enter" -> c.dimensionEnterEnabled = parseBoolean(v, c.dimensionEnterEnabled);
                case "triggers.major-craft" -> c.majorCraftEnabled = parseBoolean(v, c.majorCraftEnabled);
                case "triggers.biome-discovery" -> c.biomeDiscoveryEnabled = parseBoolean(v, c.biomeDiscoveryEnabled);
                case "triggers.taming" -> c.tamingEnabled = parseBoolean(v, c.tamingEnabled);
                case "triggers.world-first" -> c.worldFirstEnabled = parseBoolean(v, c.worldFirstEnabled);
                case "triggers.manual-crystal" -> c.manualCrystalEnabled = parseBoolean(v, c.manualCrystalEnabled);
                case "triggers.journey-tier1-distance" ->
                    c.journeyTier1Distance = parseInt(v, c.journeyTier1Distance);
                case "triggers.journey-tier2-distance" ->
                    c.journeyTier2Distance = parseInt(v, c.journeyTier2Distance);

                case "decay.enabled" -> c.decayEnabled = parseBoolean(v, c.decayEnabled);
                case "decay.whisper-days" -> c.whisperDays = parseInt(v, c.whisperDays);
                case "decay.mark-days" -> c.markDays = parseInt(v, c.markDays);
                case "decay.scar-days" -> c.scarDays = parseInt(v, c.scarDays);
                case "decay.world-first-days" -> c.worldFirstDays = parseInt(v, c.worldFirstDays);

                case "limits.max-echoes-per-chunk" -> c.maxEchoesPerChunk = parseInt(v, c.maxEchoesPerChunk);
                case "limits.max-echoes-per-player" -> c.maxEchoesPerPlayer = parseInt(v, c.maxEchoesPerPlayer);
                case "limits.max-echoes-global" -> c.maxEchoesGlobal = parseInt(v, c.maxEchoesGlobal);

                case "playback.trigger-radius" -> c.triggerRadius = parseInt(v, c.triggerRadius);
                case "playback.max-concurrent" -> c.maxConcurrentPerPlayer = parseInt(v, c.maxConcurrentPerPlayer);
                case "playback.whisper-opacity" -> c.whisperOpacity = parseFloat(v, c.whisperOpacity);
                case "playback.mark-opacity" -> c.markOpacity = parseFloat(v, c.markOpacity);
                case "playback.scar-opacity" -> c.scarOpacity = parseFloat(v, c.scarOpacity);

                case "performance.scan-interval-ticks" ->
                    c.scanIntervalTicks = parseInt(v, c.scanIntervalTicks);
                case "performance.async-recording" -> c.asyncRecording = parseBoolean(v, c.asyncRecording);

                case "privacy.hide-player-names" -> c.hidePlayerNames = parseBoolean(v, c.hidePlayerNames);
                case "privacy.anonymize-all" -> c.anonymizeAll = parseBoolean(v, c.anonymizeAll);

                default -> {
                    // Unknown key: ignore for forward/backward compatibility.
                }
            }
        } catch (Exception e) {
            Echoes.LOGGER.warn("Failed to parse config key '{}' with value '{}'", key, rawValue, e);
        }
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        if ("true".equalsIgnoreCase(value))
            return true;
        if ("false".equalsIgnoreCase(value))
            return false;
        return fallback;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float parseFloat(String value, float fallback) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String defaultToml() {
        return """
                [meta]
                config-version = 1

                [general]
                enabled = true
                self-echoes-visible = true
                allow-player-optout = true

                [triggers]
                death = true
                structure-discovery = true
                boss-kill = true
                dimension-enter = true
                major-craft = true
                biome-discovery = true
                taming = true
                world-first = true
                manual-crystal = true

                # Journey distance thresholds (in blocks)
                journey-tier1-distance = 500
                journey-tier2-distance = 2000

                [decay]
                enabled = true
                whisper-days = 7
                mark-days = 30
                scar-days = 60
                world-first-days = -1

                [limits]
                max-echoes-per-chunk = 8
                max-echoes-per-player = 50
                max-echoes-global = 2000

                [playback]
                trigger-radius = 16
                # Currently only 1 is supported
                max-concurrent = 1
                whisper-opacity = 0.25
                mark-opacity = 0.45
                scar-opacity = 0.70

                [performance]
                scan-interval-ticks = 10
                async-recording = true

                [privacy]
                hide-player-names = false
                anonymize-all = false
                """;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public int getConfigVersion() {
        return configVersion;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isSelfEchoesVisible() {
        return selfEchoesVisible;
    }

    public boolean isAllowPlayerOptout() {
        return allowPlayerOptout;
    }

    public boolean isDeathEnabled() {
        return deathEnabled;
    }

    public boolean isStructureDiscoveryEnabled() {
        return structureDiscoveryEnabled;
    }

    public boolean isBossKillEnabled() {
        return bossKillEnabled;
    }

    public boolean isDimensionEnterEnabled() {
        return dimensionEnterEnabled;
    }

    public boolean isMajorCraftEnabled() {
        return majorCraftEnabled;
    }

    public boolean isBiomeDiscoveryEnabled() {
        return biomeDiscoveryEnabled;
    }

    public boolean isTamingEnabled() {
        return tamingEnabled;
    }

    public boolean isWorldFirstEnabled() {
        return worldFirstEnabled;
    }

    public boolean isManualCrystalEnabled() {
        return manualCrystalEnabled;
    }

    public int getJourneyTier1Distance() {
        return journeyTier1Distance;
    }

    public int getJourneyTier2Distance() {
        return journeyTier2Distance;
    }

    public boolean isDecayEnabled() {
        return decayEnabled;
    }

    public int getWhisperDays() {
        return whisperDays;
    }

    public int getMarkDays() {
        return markDays;
    }

    public int getScarDays() {
        return scarDays;
    }

    public int getWorldFirstDays() {
        return worldFirstDays;
    }

    public int getMaxEchoesPerChunk() {
        return maxEchoesPerChunk;
    }

    public int getMaxEchoesPerPlayer() {
        return maxEchoesPerPlayer;
    }

    public int getMaxEchoesGlobal() {
        return maxEchoesGlobal;
    }

    public int getTriggerRadius() {
        return triggerRadius;
    }

    public int getMaxConcurrentPerPlayer() {
        return maxConcurrentPerPlayer;
    }

    public float getWhisperOpacity() {
        return whisperOpacity;
    }

    public float getMarkOpacity() {
        return markOpacity;
    }

    public float getScarOpacity() {
        return scarOpacity;
    }

    public int getScanIntervalTicks() {
        return scanIntervalTicks;
    }

    public boolean isAsyncRecording() {
        return asyncRecording;
    }

    public boolean isHidePlayerNames() {
        return hidePlayerNames;
    }

    public boolean isAnonymizeAll() {
        return anonymizeAll;
    }

    private static boolean debug = false;

    public static boolean isDebug() {
        return debug;
    }

    public static void setDebug(boolean value) {
        debug = value;
    }

    public static boolean toggleDebug() {
        debug = !debug;
        return debug;
    }
}
