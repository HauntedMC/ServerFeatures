package nl.hauntedmc.serverfeatures.internal.cache;

import java.util.List;
import java.util.Map;

/**
 * Common on-disk cache store API that always works in terms of {@link CacheValue}.
 */
public interface FileCacheStore extends CacheStore {
    /**
     * Overwrite the list under {@code key} with a single {@link CacheValue}, discarding any existing data.
     */
    void setEntry(String key, Map<String, Object> data, long ttlMillis);

    /** First live entry under {@code key}, or {@code null} if none. */
    CacheValue getEntry(String key);

    /** All live entries under {@code key}. */
    List<CacheValue> getEntries(String key);

    /** All live entries, keyed by their cache-key. */
    Map<String, List<CacheValue>> getAllEntries();

    /** All live entries whose key matches the given regex. */
    Map<String, List<CacheValue>> getMatchingEntries(String regex);
}
