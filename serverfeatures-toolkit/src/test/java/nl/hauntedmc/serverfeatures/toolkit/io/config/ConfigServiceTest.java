package nl.hauntedmc.serverfeatures.toolkit.io.config;

import nl.hauntedmc.serverfeatures.toolkit.ToolkitContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void openCachesFilesAndRejectsPathEscape() {
        ConfigService service = new ConfigService(tempDir, mock(Logger.class), getClass().getClassLoader());
        YamlFile first = service.open("config.yml", false);
        YamlFile second = service.open("config.yml", false);

        assertSame(first, second);
        assertTrue(service.exists("config.yml"));
        assertThrows(IllegalArgumentException.class, () -> service.open("../evil.yml", false));
    }

    @Test
    void openCopiesDefaultsOrCreatesEmptyFile() throws Exception {
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
            @Override
            public InputStream getResourceAsStream(String name) {
                if ("defaults.yml".equals(name)) {
                    return new ByteArrayInputStream("value: 7\n".getBytes(StandardCharsets.UTF_8));
                }
                return null;
            }
        };
        ConfigService service = new ConfigService(tempDir, mock(Logger.class), loader);

        service.open("defaults.yml", true);
        assertTrue(Files.readString(tempDir.resolve("defaults.yml")).contains("value: 7"));
        service.open("empty.yml", true);
        assertTrue(Files.exists(tempDir.resolve("empty.yml")));
        assertTrue(service.openExisting("missing.yml").isEmpty());
        assertTrue(service.openExisting("defaults.yml").isPresent());
    }

    @Test
    void contextConstructorAndViewHelpersWork() {
        ToolkitContext context = mock(ToolkitContext.class);
        when(context.getDataDirectory()).thenReturn(tempDir);
        when(context.getLogger()).thenReturn(mock(Logger.class));
        when(context.getResourceClassLoader()).thenReturn(getClass().getClassLoader());

        ConfigService service = new ConfigService(context);
        ConfigView root = service.view("root.yml", false);
        ConfigView scoped = service.view("scoped.yml", false, "global");
        root.put("value", 3);
        scoped.put("name", "server");

        assertEquals(3, root.get("value", Integer.class));
        assertEquals("server", scoped.get("name", String.class));
        assertNotNull(service.resolve("root.yml"));
    }

    @Test
    void openWrapsFilesystemFailure() throws Exception {
        Path blocked = tempDir.resolve("blocked");
        Files.writeString(blocked, "file");
        ConfigService service = new ConfigService(blocked, mock(Logger.class), null);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.open("nested.yml", false));
        assertTrue(failure.getMessage().contains("Failed to open YAML file"));
    }
}
