package nl.hauntedmc.serverfeatures.features.limitspawners.internal;

import nl.hauntedmc.serverfeatures.features.limitspawners.model.SpawnerKey;
import nl.hauntedmc.serverfeatures.framework.log.FeatureLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnerPositionStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void atomicallyRoundTripsPositionSnapshot() {
        Path file = temporaryDirectory.resolve("spawner-index.json");
        SpawnerPositionStore store = new SpawnerPositionStore(file, logger());
        SpawnerKey first = new SpawnerKey(UUID.randomUUID(), 12, 64, -20);
        SpawnerKey second = new SpawnerKey(UUID.randomUUID(), -5, -10, 9);

        store.save(List.of(first, second));

        assertEquals(List.of(first, second), store.load());
        assertTrue(Files.isRegularFile(file));
    }

    @Test
    void corruptIndexIsQuarantinedInsteadOfBlockingStartup() throws IOException {
        Path file = temporaryDirectory.resolve("spawner-index.json");
        Files.writeString(file, "{broken-json", StandardCharsets.UTF_8);
        SpawnerPositionStore store = new SpawnerPositionStore(file, logger());

        assertTrue(store.load().isEmpty());
        assertFalse(Files.exists(file));
        try (var files = Files.list(temporaryDirectory)) {
            assertTrue(files.anyMatch(path -> path.getFileName().toString()
                    .startsWith("spawner-index.json.corrupt-")));
        }
    }

    private static FeatureLogger logger() {
        return new FeatureLogger(
                Logger.getLogger(SpawnerPositionStoreTest.class.getName()),
                "LimitSpawners"
        );
    }
}
