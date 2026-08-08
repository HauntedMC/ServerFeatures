package nl.hauntedmc.serverfeatures.api.io.cache.impl;

import nl.hauntedmc.serverfeatures.api.io.cache.CacheStore;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Stub SQLite-backed cache file. Records TTL cleanup at application layer.
 */
public class SqliteCacheFile implements CacheStore {
    private final File file;

    public SqliteCacheFile(File file) {
        this.file = file;
        try {
            file.getParentFile().mkdirs();
            file.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public File getUnderlyingFile() {
        return file;
    }

    public Connection getConnection() {
        try {
            return DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void cleanupExpired() { /* no-op */ }

    @Override
    public void delete() {
        if (!file.delete()) file.deleteOnExit();
    }

    @Override
    public boolean isEmpty() {
        return false;
    }
}