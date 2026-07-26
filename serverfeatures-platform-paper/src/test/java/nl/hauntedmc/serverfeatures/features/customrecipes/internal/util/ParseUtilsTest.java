package nl.hauntedmc.serverfeatures.features.customrecipes.internal.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ParseUtilsTest {

    @Test
    void parseItemStackReturnsNullForInvalidInput() {
        assertNull(ParseUtils.parseItemStack(null));
        assertNull(ParseUtils.parseItemStack(""));
        assertNull(ParseUtils.parseItemStack("not_a_material"));
    }

    @Test
    void parseNumericHelpersFallBackOnInvalidValues() {
        assertEquals(1.5F, ParseUtils.parseFloat("1.5", 0.0F));
        assertEquals(0.0F, ParseUtils.parseFloat("x", 0.0F));
        assertEquals(7, ParseUtils.parseInt("7", 0));
        assertEquals(0, ParseUtils.parseInt("x", 0));
    }
}
