package nl.hauntedmc.serverfeatures.features.holograms.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HologramDefinitionTest {

    @Test
    void parseArgbSupportsRgbAndArgbAndRejectsInvalid() {
        assertEquals(0xFF112233, HologramDefinition.parseARGB("#112233"));
        assertEquals(0x80112233, HologramDefinition.parseARGB("80112233"));
        assertNull(HologramDefinition.parseARGB("zzz"));
        assertNull(HologramDefinition.parseARGB(null));
    }

    @Test
    void parseIntFallsBackToDefaultOnInvalid() {
        assertEquals(7, HologramDefinition.parseInt("7", 1));
        assertEquals(1, HologramDefinition.parseInt("nope", 1));
        assertEquals(1, HologramDefinition.parseInt(null, 1));
    }

    @Test
    void canonicalConstructorClampsLineWidthAndBrightness() {
        HologramDefinition def = new HologramDefinition(
                "id", "world", 0, 0, 0, 0f, 0f,
                null, null,
                -5, false, false, false,
                (Integer) null, false, (Integer) null, null,
                -4, 22
        );

        assertEquals(0, def.lineWidth());
        assertEquals(0, def.brightnessBlock());
        assertEquals(15, def.brightnessSky());
    }
}
