package nl.hauntedmc.serverfeatures.api.io.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigNodeTest {

    @Test
    void supportsTraversalAndTypedReads() {
        ConfigNode node = ConfigNode.ofRaw(Map.of(
                "enabled", "true",
                "limits", Map.of("max", "10"),
                "list", List.of("a", "b")
        ), "<root>");

        assertEquals(true, node.get("enabled").as(Boolean.class, false));
        assertEquals(10, node.getAt("limits.max").as(Integer.class, 0));
        assertEquals(List.of("a", "b"), node.get("list").listOf(String.class));
        assertTrue(node.keys().contains("limits"));
        assertEquals("<root>.limits.max", node.getAt("limits.max").path());
    }

    @Test
    void mapChildrenAndMergedStringListWork() {
        ConfigNode node = ConfigNode.ofRaw(Map.of(
                "a", "x",
                "single", "one",
                "many", List.of("two", "three")
        ), "root");

        assertEquals("x", node.children().get("a").as(String.class, null));
        assertEquals(List.of("one", "two", "three"), node.mergedStringList("single", "many"));
        assertEquals(Map.of("a", "x", "single", "one", "many", List.of("two", "three")),
                node.mapValues(Object.class));
    }

    @Test
    void asRequiredThrowsWhenMissing() {
        ConfigNode missing = ConfigNode.ofRaw(Map.of(), "root").get("missing");
        assertThrows(IllegalStateException.class, () -> missing.asRequired(String.class));
    }
}
