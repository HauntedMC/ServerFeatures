package nl.hauntedmc.serverfeatures.framework.config;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigService;
import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainConfigHandlerTest {

    @TempDir
    Path dataDirectory;

    @Test
    void migratesLegacyFeatureSettingsToAnIndependentFile() {
        ConfigService service = new ConfigService(plugin(dataDirectory));
        MainConfigHandler main = new MainConfigHandler(Logger.getLogger("main-config-test"), service);
        main.put("features.Demo.enabled", true);
        main.put("features.Demo.nested.value", 42);

        main.migrateLegacyFeatureConfig("Demo");

        FeatureConfigHandler feature = main.openFeatureConfig("Demo");
        assertEquals(true, feature.get("enabled", Boolean.class));
        assertEquals(42, feature.get("nested.value", Integer.class));
        assertTrue(main.node("features.Demo").isNull());
        assertTrue(Files.exists(dataDirectory.resolve("features/Demo/config.yml")));
    }

    @Test
    void injectsDefaultsWithoutOverwritingExistingValuesAndExposesGlobals() {
        ConfigService service = new ConfigService(plugin(dataDirectory));
        MainConfigHandler main = new MainConfigHandler(Logger.getLogger("main-config-test"), service);
        FeatureConfigHandler feature = main.openFeatureConfig("Demo");
        feature.put("enabled", true);

        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("limit", 5);
        feature.injectDefaults(defaults);

        assertEquals(true, feature.get("enabled", Boolean.class));
        assertEquals(5, feature.get("limit", Integer.class));
        assertEquals("server", feature.getGlobalSetting("server_name", String.class));
        assertTrue(main.shouldOverwriteCommandConflicts());
        assertFalse(main.node("global").isNull());
    }

    @Test
    void preservesConfiguredCommandConflictPolicy() {
        ConfigService service = new ConfigService(plugin(dataDirectory));
        MainConfigHandler main = new MainConfigHandler(Logger.getLogger("main-config-test"), service);

        main.put("global.commands.overwrite-conflicts", false);

        assertFalse(main.shouldOverwriteCommandConflicts());
        MainConfigHandler reloaded = new MainConfigHandler(Logger.getLogger("main-config-test"), service);
        assertFalse(reloaded.shouldOverwriteCommandConflicts());
    }

    @Test
    void migrationPreservesExistingTargetBranchesWhenLegacyTypesConflict() {
        ConfigService service = new ConfigService(plugin(dataDirectory));
        MainConfigHandler main = new MainConfigHandler(Logger.getLogger("main-config-test"), service);
        FeatureConfigHandler feature = main.openFeatureConfig("Demo");
        feature.put("nested", "current");
        main.put("features.Demo.nested.legacy", 42);
        main.put("features.Demo.added", true);

        main.migrateLegacyFeatureConfig("Demo");

        assertEquals("current", feature.get("nested", String.class));
        assertNull(feature.get("nested.legacy"));
        assertEquals(true, feature.get("added", Boolean.class));
        assertTrue(main.node("features.Demo").isNull());
    }

    @Test
    void migrationKeepsLegacySourceWhenDestinationCannotBeSaved() throws Exception {
        ConfigService service = new ConfigService(plugin(dataDirectory));
        MainConfigHandler main = new MainConfigHandler(Logger.getLogger("main-config-test"), service);
        main.put("features.Demo.enabled", true);
        main.openFeatureConfig("Demo");
        Path destination = dataDirectory.resolve("features/Demo/config.yml");
        Files.delete(destination);
        Files.createDirectory(destination);

        assertThrows(IllegalStateException.class, () -> main.migrateLegacyFeatureConfig("Demo"));
        assertEquals(true, main.get("features.Demo.enabled", Boolean.class));
    }

    @Test
    void injectDefaultsFillsMissingNestedValuesWithoutOverwritingConfiguredChildren() {
        ConfigService service = new ConfigService(plugin(dataDirectory));
        MainConfigHandler main = new MainConfigHandler(Logger.getLogger("main-config-test"), service);
        FeatureConfigHandler feature = main.openFeatureConfig("Demo");
        feature.put("animation.steps_per_second", 5);
        Map<String, Object> animation = new LinkedHashMap<>();
        animation.put("steps_per_second", 20);
        animation.put("fade_delay", 0);
        ConfigMap defaults = new ConfigMap();
        defaults.put("animation", animation);

        feature.injectDefaults(defaults);

        assertEquals(5, feature.get("animation.steps_per_second", Integer.class));
        assertEquals(0, feature.get("animation.fade_delay", Integer.class));
    }

    private static Plugin plugin(Path dataDirectory) {
        return InterfaceProxy.of(Plugin.class, Map.of(
                "getDataFolder", args -> dataDirectory.toFile(),
                "getLogger", args -> Logger.getLogger("main-config-test")
        ));
    }
}
