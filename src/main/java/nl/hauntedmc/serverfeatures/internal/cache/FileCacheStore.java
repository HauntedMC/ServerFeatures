package nl.hauntedmc.serverfeatures.internal.cache;

import java.util.Map;

/**
 * On-disk cache store, single CacheValue per key.
 */
public interface FileCacheStore extends CacheStore {
    /** Overwrite the entry under this key. */
    void put(String key, CacheValue value);

    /** First live entry under the key, or null. */
    CacheValue get(String key);

    /** All live entries in the store, keyed by name. */
    Map<String, CacheValue> listAll();

    /** All live entries whose key matches the regex. */
    Map<String, CacheValue> find(String regex);
}