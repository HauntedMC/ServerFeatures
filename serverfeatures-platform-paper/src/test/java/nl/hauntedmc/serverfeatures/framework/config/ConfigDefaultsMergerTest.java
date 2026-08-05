package nl.hauntedmc.serverfeatures.framework.config;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigService;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigView;
import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConfigDefaultsMergerTest {

    @TempDir
    Path dataDirectory;

    @Test
    void preservesExistingBranchesWhenDefaultTypesConflict() {
        ConfigView target = new ConfigService(plugin()).view("target.yml", false);
        target.put("scalarBranch", "current");
        target.put("mapBranch.current", 1);

        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("scalarBranch", Map.of("defaultChild", 2));
        defaults.put("mapBranch", Map.of("current", 99, "missing", 3));
        defaults.put("newBranch", Map.of("value", 4));

        var additions = ConfigDefaultsMerger.mergeMissingPaths(target, defaults);

        assertEquals(2, additions.size());
        assertEquals("current", target.get("scalarBranch", String.class));
        assertNull(target.get("scalarBranch.defaultChild"));
        assertEquals(1, target.get("mapBranch.current", Integer.class));
        assertEquals(3, target.get("mapBranch.missing", Integer.class));
        assertEquals(4, target.get("newBranch.value", Integer.class));
    }

    @Test
    void isIdempotent() {
        ConfigView target = new ConfigService(plugin()).view("target.yml", false);
        Map<String, Object> defaults = Map.of("nested", Map.of("value", 4));

        assertEquals(1, ConfigDefaultsMerger.mergeMissingPaths(target, defaults).size());
        assertEquals(0, ConfigDefaultsMerger.mergeMissingPaths(target, defaults).size());
        assertEquals(4, target.get("nested.value", Integer.class));
    }

    private Plugin plugin() {
        return InterfaceProxy.of(Plugin.class, Map.of(
                "getDataFolder", args -> dataDirectory.toFile(),
                "getLogger", args -> Logger.getLogger("config-defaults-test")
        ));
    }
}
