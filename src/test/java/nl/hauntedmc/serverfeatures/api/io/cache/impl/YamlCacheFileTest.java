package nl.hauntedmc.serverfeatures.api.io.cache.impl;

import nl.hauntedmc.serverfeatures.api.io.cache.CacheValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlCacheFileTest {

    @TempDir
    Path tmp;

    @Test
    void putGetFindRemoveAndCleanupExpired() {
        File file = tmp.resolve("yaml/store.yml").toFile();
        YamlCacheFile store = new YamlCacheFile(file);

        Map<String, Object> liveData = new LinkedHashMap<>();
        liveData.put("name", "alex");
        Map<String, Object> expiredData = new LinkedHashMap<>();
        expiredData.put("name", "old");
        CacheValue live = CacheValue.of(liveData, System.currentTimeMillis() + 60_000);
        CacheValue expired = CacheValue.of(expiredData, System.currentTimeMillis() - 1);
        store.put("live-user", live);
        store.put("old-user", expired);

        assertTrue(store.getKeys().contains("live-user"));
        assertTrue(store.getKeys().contains("old-user"));

        store.cleanupExpired();
        assertTrue(store.getKeys().contains("live-user"));

        store.remove("old-user");
        store.remove("live-user");
        assertTrue(store.isEmpty());
    }
}
