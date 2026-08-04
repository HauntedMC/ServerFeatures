package nl.hauntedmc.serverfeatures.features.graveyard.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GraveTimerFormattingTest {
    @Test
    void keepsSecondPrecisionAcrossTheWholeDefaultLifetime() {
        assertEquals("10m 0s", GraveManager.formatDuration(600_000L));
        assertEquals("9m 59s", GraveManager.formatDuration(599_000L));
        assertEquals("5m 1s", GraveManager.formatDuration(301_000L));
        assertEquals("5m 0s", GraveManager.formatDuration(300_000L));
    }
}
