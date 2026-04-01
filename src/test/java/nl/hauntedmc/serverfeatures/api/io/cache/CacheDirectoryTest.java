package nl.hauntedmc.serverfeatures.api.io.cache;

import nl.hauntedmc.serverfeatures.api.io.cache.impl.JsonCacheFile;
import nl.hauntedmc.serverfeatures.api.io.cache.impl.SqliteCacheFile;
import nl.hauntedmc.serverfeatures.api.io.cache.impl.YamlCacheFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheDirectoryTest {

    @TempDir
    Path tmp;

    @Test
    void createsDirectoryAndReturnsStoreByType() {
        CacheDirectory dir = new CacheDirectory(tmp.toFile(), "feature", "queue");
        assertTrue(dir.getDirectory().exists());
        assertTrue(dir.getDirectory().getName().contains("feature-queue"));

        CacheStore yaml = dir.getStore("state", CacheType.YAML);
        CacheStore json = dir.getStore("state", CacheType.JSON);
        CacheStore sqlite = dir.getStore("state", CacheType.SQLITE);

        assertInstanceOf(YamlCacheFile.class, yaml);
        assertInstanceOf(JsonCacheFile.class, json);
        assertInstanceOf(SqliteCacheFile.class, sqlite);
        assertEquals("state.yml", yaml.getUnderlyingFile().getName());
        assertEquals("state.json", json.getUnderlyingFile().getName());
        assertEquals("state.db", sqlite.getUnderlyingFile().getName());
    }
}
