package nl.hauntedmc.serverfeatures.features.actionbar.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActionbarFeatureServiceTest {

    @Test
    void ceilTicksToSecondsRoundsUpPerTwentyTicks() {
        assertEquals(0, ActionbarFeatureService.ceilTicksToSeconds(0L));
        assertEquals(0, ActionbarFeatureService.ceilTicksToSeconds(-1L));
        assertEquals(1, ActionbarFeatureService.ceilTicksToSeconds(1L));
        assertEquals(1, ActionbarFeatureService.ceilTicksToSeconds(20L));
        assertEquals(2, ActionbarFeatureService.ceilTicksToSeconds(21L));
        assertEquals(2, ActionbarFeatureService.ceilTicksToSeconds(40L));
    }
}
