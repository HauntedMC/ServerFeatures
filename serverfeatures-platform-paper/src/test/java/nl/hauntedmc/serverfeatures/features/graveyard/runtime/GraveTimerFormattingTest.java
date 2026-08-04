package nl.hauntedmc.serverfeatures.features.graveyard.runtime;

import nl.hauntedmc.serverfeatures.features.graveyard.text.GraveyardText;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GraveTimerFormattingTest {
    @Test
    void keepsSecondPrecisionAcrossTheWholeDefaultLifetime() {
        assertEquals(
                new GraveyardText.DurationParts(600L, 0L, 10L, 0L),
                GraveyardText.durationParts(600_000L)
        );
        assertEquals(
                new GraveyardText.DurationParts(599L, 0L, 9L, 59L),
                GraveyardText.durationParts(599_000L)
        );
        assertEquals(
                new GraveyardText.DurationParts(301L, 0L, 5L, 1L),
                GraveyardText.durationParts(301_000L)
        );
        assertEquals(
                new GraveyardText.DurationParts(300L, 0L, 5L, 0L),
                GraveyardText.durationParts(300_000L)
        );
    }
}
