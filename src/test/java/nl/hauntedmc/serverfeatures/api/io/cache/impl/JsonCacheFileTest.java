package nl.hauntedmc.serverfeatures.api.io.cache.impl;

import nl.hauntedmc.serverfeatures.api.io.cache.CacheValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonCacheFileTest {

    @TempDir
    Path tmp;

    @Test
    void putGetFindRemoveAndCleanupExpired() {
        File file = tmp.resolve("json/store.json").toFile();
        JsonCacheFile store = new JsonCacheFile(file);

        CacheValue live = CacheValue.of(Map.of("name", "alex"), System.currentTimeMillis() + 60_000);
        CacheValue expired = CacheValue.of(Map.of("name", "old"), System.currentTimeMillis() - 1);
        store.put("live-user", live);
        store.put("old-user", expired);

        assertEquals("alex", store.get("live-user").getData().get("name"));
        assertEquals(1, store.find("live-.*").size());

        store.cleanupExpired();
        assertNull(store.get("old-user"));
        assertTrue(store.getKeys().contains("live-user"));

        store.remove("live-user");
        assertTrue(store.isEmpty());
    }
}
