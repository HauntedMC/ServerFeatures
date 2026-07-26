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

class ConfigMigrationMergerTest {

    @TempDir
    Path dataDirectory;

    @Test
    void preservesExistingBranchesWhenLegacyTypesConflict() {
        ConfigView target = new ConfigService(plugin()).view("target.yml", false);
        target.put("scalarBranch", "current");
        target.put("mapBranch.current", 1);

        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("scalarBranch", Map.of("legacyChild", 2));
        legacy.put("mapBranch", Map.of("current", 99, "missing", 3));
        legacy.put("newBranch", Map.of("value", 4));

        int additions = ConfigMigrationMerger.mergeMissing(target, legacy);

        assertEquals(2, additions);
        assertEquals("current", target.get("scalarBranch", String.class));
        assertNull(target.get("scalarBranch.legacyChild"));
        assertEquals(1, target.get("mapBranch.current", Integer.class));
        assertEquals(3, target.get("mapBranch.missing", Integer.class));
        assertEquals(4, target.get("newBranch.value", Integer.class));
    }

    @Test
    void isIdempotent() {
        ConfigView target = new ConfigService(plugin()).view("target.yml", false);
        Map<String, Object> legacy = Map.of("nested", Map.of("value", 4));

        assertEquals(1, ConfigMigrationMerger.mergeMissing(target, legacy));
        assertEquals(0, ConfigMigrationMerger.mergeMissing(target, legacy));
        assertEquals(4, target.get("nested.value", Integer.class));
    }

    private Plugin plugin() {
        return InterfaceProxy.of(Plugin.class, Map.of(
                "getDataFolder", args -> dataDirectory.toFile(),
                "getLogger", args -> Logger.getLogger("config-migration-test")
        ));
    }
}
