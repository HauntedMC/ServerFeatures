package nl.hauntedmc.serverfeatures.api.io.cache;

import java.io.File;

/**
 * Common operations for any on‐disk cache “file” or store.
 */
public interface CacheStore {
    /**
     * Returns the underlying file or directory backing this store.
     */
    File getUnderlyingFile();

    /**
     * Purge expired entries (per-entry TTL) and update disk if needed.
     */
    void cleanupExpired();

    /**
     * Delete the underlying store (file or directory).
     */
    void delete();

    /**
     * @return true if no entries remain in this store.
     */
    boolean isEmpty();
}
