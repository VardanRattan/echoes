package com.vardanrattan.echoes.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EchoesConfig#parseInto}.
 *
 * {@code parseInto} is a pure function (no file I/O, no Fabric loader),
 * so these tests run in a plain JVM without any Minecraft bootstrap.
 */
public class EchoesConfigTest {

    @Test
    void unknownKeyIsIgnored() {
        EchoesConfig cfg = new EchoesConfig();
        EchoesConfig.parseInto(cfg, List.of(
                "[general]",
                "unknown-key = banana",
                "enabled = false"));
        assertFalse(cfg.isEnabled(), "enabled should have been set to false");
        // No exception thrown — unknown key was silently ignored
    }

    @Test
    void defaultFallbackOnBadInteger() {
        EchoesConfig cfg = new EchoesConfig();
        EchoesConfig.parseInto(cfg, List.of(
                "[limits]",
                "max-echoes-per-chunk = not_a_number"));
        assertEquals(8, cfg.getMaxEchoesPerChunk(),
                "Bad integer should fall back to the default value of 8");
    }

    @Test
    void parsesEnabledFalse() {
        EchoesConfig cfg = new EchoesConfig();
        EchoesConfig.parseInto(cfg, List.of("[general]", "enabled = false"));
        assertFalse(cfg.isEnabled());
    }

    @Test
    void parsesEnabledTrue() {
        EchoesConfig cfg = new EchoesConfig();
        EchoesConfig.parseInto(cfg, List.of("[general]", "enabled = true"));
        assertTrue(cfg.isEnabled());
    }

    @Test
    void parsesAllDecayDays() {
        EchoesConfig cfg = new EchoesConfig();
        EchoesConfig.parseInto(cfg, List.of(
                "[decay]",
                "whisper-days = 3",
                "mark-days = 15",
                "scar-days = 45",
                "world-first-days = -1"));
        assertEquals(3, cfg.getWhisperDays());
        assertEquals(15, cfg.getMarkDays());
        assertEquals(45, cfg.getScarDays());
        assertEquals(-1, cfg.getWorldFirstDays());
    }

    @Test
    void parsesOpacityFloats() {
        EchoesConfig cfg = new EchoesConfig();
        EchoesConfig.parseInto(cfg, List.of(
                "[playback]",
                "whisper-opacity = 0.1",
                "mark-opacity = 0.5",
                "scar-opacity = 0.9"));
        assertEquals(0.1f, cfg.getWhisperOpacity(), 0.0001f);
        assertEquals(0.5f, cfg.getMarkOpacity(), 0.0001f);
        assertEquals(0.9f, cfg.getScarOpacity(), 0.0001f);
    }

    @Test
    void emptyInputProducesAllDefaults() {
        EchoesConfig cfg = new EchoesConfig();
        EchoesConfig.parseInto(cfg, List.of());
        // Spot-check a handful of defaults from EchoesConfig field initialisers
        assertTrue(cfg.isEnabled());
        assertTrue(cfg.isDecayEnabled());
        assertEquals(16, cfg.getTriggerRadius());
        assertEquals(8, cfg.getMaxEchoesPerChunk());
        assertEquals(2000, cfg.getMaxEchoesGlobal());
    }

    @Test
    void commentsAndBlankLinesAreSkipped() {
        EchoesConfig cfg = new EchoesConfig();
        EchoesConfig.parseInto(cfg, List.of(
                "# this is a comment",
                "",
                "[general]",
                "# another comment",
                "enabled = false"));
        assertFalse(cfg.isEnabled());
    }

    @Test
    void privacyFlagsAreParsed() {
        EchoesConfig cfg = new EchoesConfig();
        EchoesConfig.parseInto(cfg, List.of(
                "[privacy]",
                "hide-player-names = true",
                "anonymize-all = true"));
        assertTrue(cfg.isHidePlayerNames());
        assertTrue(cfg.isAnonymizeAll());
    }
}
