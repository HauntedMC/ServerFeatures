package nl.hauntedmc.serverfeatures.features.limitspawners.internal;

import nl.hauntedmc.serverfeatures.features.limitspawners.model.SpawnerKey;
import nl.hauntedmc.serverfeatures.features.limitspawners.model.TrackedSpawnerMob;
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

class SpawnerMobStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void atomicallyRoundTripsRegistrySnapshot() {
        Path file = temporaryDirectory.resolve("tracked-mobs.json");
        SpawnerMobStore store = new SpawnerMobStore(file, logger());
        UUID worldId = UUID.randomUUID();
        TrackedSpawnerMob record = new TrackedSpawnerMob(
                UUID.randomUUID(),
                new SpawnerKey(worldId, 12, 64, -20),
                worldId,
                4,
                -2
        );

        store.save(List.of(record));

        assertEquals(List.of(record), store.load());
        assertTrue(Files.isRegularFile(file));
    }

    @Test
    void corruptRegistryIsQuarantinedInsteadOfBlockingFeatureStartup() throws IOException {
        Path file = temporaryDirectory.resolve("tracked-mobs.json");
        Files.writeString(file, "{broken-json", StandardCharsets.UTF_8);
        SpawnerMobStore store = new SpawnerMobStore(file, logger());

        assertTrue(store.load().isEmpty());
        assertFalse(Files.exists(file));
        try (var files = Files.list(temporaryDirectory)) {
            assertTrue(files.anyMatch(path -> path.getFileName().toString()
                    .startsWith("tracked-mobs.json.corrupt-")));
        }
    }

    private static FeatureLogger logger() {
        return new FeatureLogger(Logger.getLogger(SpawnerMobStoreTest.class.getName()), "LimitSpawners");
    }
}
