package nl.hauntedmc.serverfeatures.api.io.config;

import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigServiceTest {

    @TempDir
    Path tmp;

    @Test
    void openCreatesMissingFileWhenNoDefaultsAreCopied() throws Exception {
        Plugin plugin = plugin(tmp, Map.of(), new AtomicInteger());
        ConfigService service = new ConfigService(plugin);

        YamlFile yamlFile = service.open("nested/test.yml", false);

        assertNotNull(yamlFile);
        assertTrue(Files.exists(tmp.resolve("nested/test.yml")));
    }

    @Test
    void openCopiesDefaultResourceWhenPresent() throws Exception {
        AtomicInteger saveCalls = new AtomicInteger();
        Plugin plugin = plugin(tmp, Map.of("config.yml", "server_name: haunted\n"), saveCalls);
        ConfigService service = new ConfigService(plugin);

        service.open("config.yml", true);

        assertEquals(1, saveCalls.get());
        assertEquals("server_name: haunted\n", Files.readString(tmp.resolve("config.yml")));
    }

    @Test
    void openCachesYamlFilesByNormalizedAbsolutePath() {
        Plugin plugin = plugin(tmp, Map.of(), new AtomicInteger());
        ConfigService service = new ConfigService(plugin);

        YamlFile a = service.open("a/../config.yml", false);
        YamlFile b = service.open("config.yml", false);

        assertSame(a, b);
    }

    @Test
    void scopedViewWritesToExpectedPath() {
        Plugin plugin = plugin(tmp, Map.of(), new AtomicInteger());
        ConfigService service = new ConfigService(plugin);

        ConfigView scoped = service.view("settings.yml", false, "feature.demo");
        scoped.put("enabled", true);

        ConfigView root = service.view("settings.yml", false);
        assertEquals(true, root.get("feature.demo.enabled", Boolean.class));
    }

    @Test
    void rejectsPathsThatEscapeThePluginDataDirectory() {
        ConfigService service = new ConfigService(plugin(tmp, Map.of(), new AtomicInteger()));

        assertThrows(IllegalArgumentException.class, () -> service.resolve("../outside.yml"));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(tmp.resolveSibling("outside.yml").toString()));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(tmp.resolve("inside.yml").toString()));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(" "));
    }

    @Test
    void openExistingDoesNotCreateMissingFiles() {
        ConfigService service = new ConfigService(plugin(tmp, Map.of(), new AtomicInteger()));

        assertTrue(service.openExisting("missing.yml").isEmpty());
        assertTrue(Files.notExists(tmp.resolve("missing.yml")));
    }

    @Test
    void rejectsDirectoriesAsYamlFiles() throws Exception {
        Files.createDirectories(tmp.resolve("config.yml"));
        ConfigService service = new ConfigService(plugin(tmp, Map.of(), new AtomicInteger()));

        assertThrows(IllegalStateException.class, () -> service.open("config.yml", false));
    }

    @Test
    void writesYamlThroughAtomicReplacementWithoutLeavingTemporaryFiles() throws Exception {
        ConfigService service = new ConfigService(plugin(tmp, Map.of(), new AtomicInteger()));
        ConfigView view = service.view("nested/config.yml", false);

        view.put("value", 42);

        assertEquals(42, service.view("nested/config.yml", false).get("value", Integer.class));
        try (var files = Files.list(tmp.resolve("nested"))) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void propagatesPersistenceFailures() throws Exception {
        ConfigService service = new ConfigService(plugin(tmp, Map.of(), new AtomicInteger()));
        ConfigView view = service.view("features/Demo/config.yml", false);
        Path target = tmp.resolve("features/Demo/config.yml");
        Files.delete(target);
        Files.createDirectory(target);

        assertThrows(IllegalStateException.class, () -> view.put("enabled", true));
    }

    private static Plugin plugin(Path dataFolder, Map<String, String> resources, AtomicInteger saveCalls) {
        return InterfaceProxy.of(Plugin.class, Map.of(
                "getDataFolder", args -> dataFolder.toFile(),
                "getLogger", args -> Logger.getLogger("config-service-test"),
                "getResource", args -> {
                    String path = (String) args[0];
                    String body = resources.get(path);
                    if (body == null) return null;
                    return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
                },
                "saveResource", args -> {
                    String path = (String) args[0];
                    String body = resources.get(path);
                    saveCalls.incrementAndGet();
                    if (body == null) {
                        return null;
                    }
                    try {
                        Path target = dataFolder.resolve(path);
                        Path parent = target.getParent();
                        if (parent != null) {
                            Files.createDirectories(parent);
                        }
                        try (InputStream in = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))) {
                            Files.copy(in, target);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                }
        ));
    }
}
