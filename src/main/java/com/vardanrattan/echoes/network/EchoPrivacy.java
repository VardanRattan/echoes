package com.vardanrattan.echoes.network;

import com.vardanrattan.echoes.config.EchoesConfig;
import com.vardanrattan.echoes.data.EchoRecord;
import java.util.UUID;

/**
 * Centralizes privacy logic for echo visibility and player identification.
 * Honors hide-player-names, anonymize-all, and self-echoes-visible config flags.
 */
public final class EchoPrivacy {

    private EchoPrivacy() {
    }

    /**
     * Returns the name to display for an echo, respecting privacy settings.
     */
    public static String resolvePlayerName(EchoRecord echo) {
        if (echo == null) return "Anonymous";
        EchoesConfig cfg = EchoesConfig.get();
        if (cfg.isAnonymizeAll() || cfg.isHidePlayerNames()) {
            return "Anonymous";
        }
        return echo.getPlayerName();
    }

    /**
     * Returns the UUID to use for skin rendering, respecting privacy settings.
     * If anonymize-all is true, returns null to trigger default skin fallback.
     */
    public static UUID resolvePlayerUuid(EchoRecord echo) {
        if (echo == null || EchoesConfig.get().isAnonymizeAll()) {
            return null;
        }
        return echo.getPlayerUuid();
    }

    /**
     * Checks if a viewer is allowed to see a specific echo based on privacy and visibility rules.
     */
    public static boolean canPlayerSeeEcho(UUID viewerUuid, EchoRecord echo) {
        if (echo == null) {
            return false;
        }
        if (viewerUuid == null) {
            return true;
        }
        EchoesConfig cfg = EchoesConfig.get();
        if (!cfg.isSelfEchoesVisible() && viewerUuid.equals(echo.getPlayerUuid())) {
            return false;
        }
        return true;
    }
}
