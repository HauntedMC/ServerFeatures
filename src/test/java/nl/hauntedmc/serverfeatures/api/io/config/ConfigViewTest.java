package nl.hauntedmc.serverfeatures.api.io.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigViewTest {

    @TempDir
    Path tmp;

    @Test
    void putGetScopeAndDefaultsWork() throws Exception {
        ConfigView root = newRootView("config.yml");
        root.put("global.name", "Haunted");
        root.put("global.max", "5");

        assertEquals("Haunted", root.get("global.name", String.class));
        assertEquals(5, root.get("global.max", Integer.class, 0));
        assertEquals("fallback", root.get("global.missing", String.class, "fallback"));

        ConfigView globals = root.scope("global");
        assertEquals("Haunted", globals.get("name", String.class));
    }

    @Test
    void putIfAbsentComputeAndListOperationsWork() throws Exception {
        ConfigView view = newRootView("ops.yml");

        assertTrue(view.putIfAbsent("counter", 1));
        assertFalse(view.putIfAbsent("counter", 2));

        int computed = view.compute("counter", Integer.class, x -> x + 1, () -> 0);
        assertEquals(2, computed);

        view.appendToList("items", "a");
        view.appendToList("items", "b");
        assertEquals(List.of("a", "b"), view.getList("items", String.class));

        int removed = view.removeFromList("items", "a"::equals);
        assertEquals(1, removed);
        assertEquals(List.of("b"), view.getList("items", String.class));
    }

    @Test
    void batchAndNodeHelpersWork() throws Exception {
        ConfigView view = newRootView("batch.yml");

        view.batch(b -> b
                .put("a", 1)
                .putIfAbsent("b", "x")
                .appendToList("l", "v1")
                .appendToList("l", "v2")
                .put("m.one", "1")
                .put("m.two", 2)
                .compute("a", Integer.class, i -> i + 1, () -> 0));

        assertEquals(2, view.get("a", Integer.class));
        assertEquals("x", view.get("b", String.class));
        assertEquals(List.of("v1", "v2"), view.node("l").listOf(String.class));
        assertEquals(2, view.getAt("a", Integer.class));
        assertEquals(2, view.nodeAt("a").as(Integer.class, 0));

        Map<String, Integer> map = view.node("m").mapValues(Integer.class);
        assertEquals(Map.of("one", 1, "two", 2), map);
    }

    private ConfigView newRootView(String fileName) throws Exception {
        Path file = tmp.resolve(fileName);
        Files.createFile(file);
        YamlFile yamlFile = new YamlFile(file, Logger.getLogger("config-view-test"));
        return new ConfigView(yamlFile, "");
    }
}
