package nl.hauntedmc.serverfeatures.features.liquidtank.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LiquidTankDataHandlerTest {

    @Test
    void parsesCoordinatesAndWorldNamesContainingUnderscores() {
        LiquidTankDataHandler.ParsedKey parsed = LiquidTankDataHandler.parseKey(
                "-123_64_456_survival_resource_world"
        );

        assertEquals(-123, parsed.x());
        assertEquals(64, parsed.y());
        assertEquals(456, parsed.z());
        assertEquals("survival_resource_world", parsed.worldName());
    }

    @Test
    void rejectsMalformedPersistenceKeys() {
        assertNull(LiquidTankDataHandler.parseKey(null));
        assertNull(LiquidTankDataHandler.parseKey("1_2_world"));
        assertNull(LiquidTankDataHandler.parseKey("x_2_3_world"));
        assertNull(LiquidTankDataHandler.parseKey("1_2_3_"));
    }
}
