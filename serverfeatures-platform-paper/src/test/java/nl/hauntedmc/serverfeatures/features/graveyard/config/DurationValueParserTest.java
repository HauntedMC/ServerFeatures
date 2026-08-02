package nl.hauntedmc.serverfeatures.features.graveyard.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DurationValueParserTest {
    @Test
    void parsesSupportedUnits() {
        assertEquals(250L, DurationValueParser.parseMillis("250ms", 1L));
        assertEquals(5_000L, DurationValueParser.parseMillis("5s", 1L));
        assertEquals(120_000L, DurationValueParser.parseMillis("2m", 1L));
        assertEquals(10_800_000L, DurationValueParser.parseMillis("3h", 1L));
        assertEquals(172_800_000L, DurationValueParser.parseMillis("2d", 1L));
    }

    @Test
    void fallsBackForInvalidNonPositiveAndOverflowValues() {
        assertEquals(42L, DurationValueParser.parseMillis("invalid", 42L));
        assertEquals(42L, DurationValueParser.parseMillis("0s", 42L));
        assertEquals(42L, DurationValueParser.parseMillis("999999999999999999999d", 42L));
        assertEquals(42L, DurationValueParser.parseMillis(null, 42L));
    }
}
