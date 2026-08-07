package nl.hauntedmc.serverfeatures.toolkit.io.cache;

import nl.hauntedmc.serverfeatures.toolkit.io.cache.impl.JsonCacheFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonCacheFileTest {

    @TempDir
    Path tempDir;

    @Test
    void valuesPersistReloadAndRegexLookupWorks() {
        Path file = tempDir.resolve("cache.json");
        JsonCacheFile cache = new JsonCacheFile(file.toFile());
        cache.put("player:one", CacheValue.of(Map.of("value", 1), System.currentTimeMillis() + 60_000));
        cache.put("other", CacheValue.of(Map.of("value", 2), System.currentTimeMillis() + 60_000));

        assertEquals(1.0d, cache.get("player:one").getData().get("value"));
        assertEquals(2, cache.listAll().size());
        assertEquals(1, cache.find("player:.*").size());

        JsonCacheFile reloaded = new JsonCacheFile(file.toFile());
        assertEquals(2, reloaded.listAll().size());
        assertFalse(reloaded.isEmpty());
    }

    @Test
    void expiredEntriesAreRemovedAndEmptyStoreDeletesFile() {
        Path file = tempDir.resolve("expired.json");
        JsonCacheFile cache = new JsonCacheFile(file.toFile());
        cache.put("expired", CacheValue.of(Map.of("x", 1), System.currentTimeMillis() - 1));

        assertNull(cache.get("expired"));
        assertTrue(cache.isEmpty());
        assertFalse(Files.exists(file));
    }

    @Test
    void deleteClearsStoreAndInvalidParentFailsCreation() throws Exception {
        Path file = tempDir.resolve("delete.json");
        JsonCacheFile cache = new JsonCacheFile(file.toFile());
        cache.put("x", CacheValue.of(Map.of("x", 1), System.currentTimeMillis() + 60_000));
        cache.delete();
        assertTrue(cache.isEmpty());

        Path blocker = tempDir.resolve("blocker");
        Files.writeString(blocker, "x");
        assertThrows(IllegalStateException.class,
                () -> new JsonCacheFile(blocker.resolve("cache.json").toFile()));
    }
}
