package nl.hauntedmc.serverfeatures.api.io.cache;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheValueTest {

    @Test
    void ofCopiesDataAndSupportsExpirationCheck() {
        CacheValue value = CacheValue.of(Map.of("k", "v"), System.currentTimeMillis() - 1);
        assertEquals("v", value.getData().get("k"));
        assertTrue(value.isExpired());
        assertThrows(UnsupportedOperationException.class, () -> value.getData().put("x", "y"));
    }

    @Test
    void builderStoresValuesAndComputesExpirationFromTtl() {
        long before = System.currentTimeMillis();
        CacheValue value = CacheValue.builder(50)
                .with("a", 1)
                .with("b", "x")
                .build();
        long after = System.currentTimeMillis();

        assertEquals(1, value.getData().get("a"));
        assertEquals("x", value.getData().get("b"));
        assertTrue(value.getExpirationTimestamp() >= before + 50);
        assertTrue(value.getExpirationTimestamp() <= after + 50);
        assertFalse(value.isExpired());
    }

    @Test
    void builderRejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> CacheValue.builder(-1));
        assertThrows(NullPointerException.class, () -> CacheValue.builder(1).with(null, "x"));
        assertThrows(NullPointerException.class, () -> CacheValue.of(null, 0));
    }
}
