package nl.hauntedmc.serverfeatures.api.io.cache.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteCacheFileTest {

    @TempDir
    Path tmp;

    @Test
    void createsUnderlyingFileAndSupportsDelete() {
        File file = tmp.resolve("sqlite/store.db").toFile();
        SqliteCacheFile store = new SqliteCacheFile(file);

        assertTrue(store.getUnderlyingFile().exists());
        assertEquals(file, store.getUnderlyingFile());
        assertFalse(store.isEmpty());

        store.cleanupExpired(); // no-op
        store.delete();
    }
}
