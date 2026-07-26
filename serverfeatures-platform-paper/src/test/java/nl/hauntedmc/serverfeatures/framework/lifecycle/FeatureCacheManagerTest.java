package nl.hauntedmc.serverfeatures.framework.lifecycle;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.cache.CacheDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeatureCacheManagerTest {

    @TempDir
    Path dataDirectory;

    @Test
    void createsTheBaseAndFeatureCacheDirectories() {
        FeatureCacheManager manager = new FeatureCacheManager(plugin(dataDirectory));

        CacheDirectory directory = manager.getCacheDirectory("Queue", "default");

        assertEquals(
                dataDirectory.resolve("cache").toFile().getAbsoluteFile(),
                directory.getDirectory().getParentFile().getAbsoluteFile()
        );
        assertTrue(directory.getDirectory().isDirectory());
    }

    @Test
    void rejectsAFileAtTheCacheDirectoryPath() throws IOException {
        Files.writeString(dataDirectory.resolve("cache"), "not-a-directory");

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> new FeatureCacheManager(plugin(dataDirectory))
        );

        assertTrue(thrown.getMessage().contains("not a directory"));
    }

    private static ServerFeatures plugin(Path dataDirectory) {
        ServerFeatures plugin = mock(ServerFeatures.class);
        when(plugin.getDataFolder()).thenReturn(dataDirectory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("cache-manager-test"));
        return plugin;
    }
}
