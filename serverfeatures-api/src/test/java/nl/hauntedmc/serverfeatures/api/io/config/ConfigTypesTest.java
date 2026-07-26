package nl.hauntedmc.serverfeatures.api.io.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigTypesTest {

    private enum Mode {A, B}

    @Test
    void convertSupportsScalarsAndEnums() {
        assertEquals("5", ConfigTypes.convert(5, String.class));
        assertEquals(true, ConfigTypes.convert(1, boolean.class));
        assertEquals(12, ConfigTypes.convert("12", int.class));
        assertEquals(42L, ConfigTypes.convert("42", long.class));
        assertEquals(1.5d, ConfigTypes.convert("1.5", double.class));
        assertEquals(2.5f, ConfigTypes.convert("2.5", float.class));
        assertEquals(Mode.B, ConfigTypes.convert("b", Mode.class));
    }

    @Test
    void convertListSupportsListAndSingleValuePromotion() {
        assertEquals(List.of(1, 2), ConfigTypes.convertList(List.of("1", 2), Integer.class));
        assertEquals(List.of("x"), ConfigTypes.convertList("x", String.class));
    }

    @Test
    void convertMapValuesConvertsEachValue() {
        Map<String, Integer> out = ConfigTypes.convertMapValues(Map.of("a", "1", "b", 2), Integer.class);
        assertEquals(Map.of("a", 1, "b", 2), out);
    }

    @Test
    void convertOrDefaultReturnsDefaultOnFailure() {
        assertEquals(7, ConfigTypes.convertOrDefault("x", Integer.class, 7));
    }

    @Test
    void invalidConversionsThrow() {
        assertThrows(IllegalArgumentException.class, () -> ConfigTypes.convert("x", Integer.class));
        assertThrows(IllegalArgumentException.class, () -> ConfigTypes.convert(List.of(1), Mode.class));
        assertThrows(IllegalArgumentException.class, () -> ConfigTypes.convertMapValues("x", Integer.class));
        assertThrows(IllegalArgumentException.class, () -> ConfigTypes.convertList(List.of("x"), Integer.class));
    }
}
