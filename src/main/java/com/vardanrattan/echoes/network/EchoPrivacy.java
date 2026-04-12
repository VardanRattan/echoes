package com.vardanrattan.echoes.network;

import com.vardanrattan.echoes.config.EchoesConfig;
import com.vardanrattan.echoes.data.EchoRecord;
import java.util.UUID;

/**
 * F6: Centralizes privacy logic for echo visibility and player identification.
 * Honors hide-player-names and anonymize-all config flags.
 */
public final class EchoPrivacy {

    private EchoPrivacy() {
    }

    /**
     * Returns the name to display for an echo, respecting privacy settings.
     */
    public static String resolvePlayerName(EchoRecord echo) {
        EchoesConfig cfg = EchoesConfig.get();
        if (cfg.isAnonymizeAll() || cfg.isHidePlayerNames()) {
            return "Anonymous";
        }
        return echo.getPlayerName();
    }

    /**
     * Returns the UUID to use for skin rendering, respecting privacy settings.
     * If anonymize-all is true, returns a null UUID to trigger default skin fallback.
     */
    public static UUID resolvePlayerUuid(EchoRecord echo) {
        if (EchoesConfig.get().isAnonymizeAll()) {
            // Returning a fixed UUID for "Anonymous" or null to use Steve/Alex fallback.
            return null;
        }
        return echo.getPlayerUuid();
    }

    /**
     * Audit point for checking if a player should be allowed to see a specific echo.
     */
    public static boolean canPlayerSeeEcho(UUID viewerUuid, EchoRecord echo) {
        // Future: could add blocklists, team-only echoes, etc.
        // For now, just a stub for future-proofing.
        return true;
    }
}
