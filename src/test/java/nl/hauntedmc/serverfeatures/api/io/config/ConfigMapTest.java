package nl.hauntedmc.serverfeatures.api.io.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMapTest {

    @Test
    void putGetContainsAndTypeChecksWork() {
        ConfigMap map = new ConfigMap()
                .put("name", "Alex")
                .put("count", 3);

        assertTrue(map.contains("name"));
        assertEquals("Alex", map.get("name"));
        assertEquals(3, map.get("count", Integer.class));
        assertThrows(ClassCastException.class, () -> map.get("count", String.class));
    }

    @Test
    void toMapReturnsDefensiveCopy() {
        ConfigMap map = new ConfigMap().put("a", 1);
        Map<String, Object> copy = map.toMap();
        copy.put("b", 2);

        assertTrue(!map.contains("b"));
    }
}
