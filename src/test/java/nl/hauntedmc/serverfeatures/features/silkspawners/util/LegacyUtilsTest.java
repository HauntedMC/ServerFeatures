package nl.hauntedmc.serverfeatures.features.silkspawners.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyUtilsTest {

    @Test
    void extractsLegacyMobTypeFromMetaString() {
        String meta = "{ms_mob:\"ZOMBIE\"}";

        assertEquals("ZOMBIE", LegacyUtils.extractLegacyMobType(meta).orElseThrow());
    }

    @Test
    void returnsEmptyWhenPatternNotPresent() {
        assertTrue(LegacyUtils.extractLegacyMobType("no mob here").isEmpty());
    }
}

