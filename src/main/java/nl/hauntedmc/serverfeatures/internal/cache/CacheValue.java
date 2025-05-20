package nl.hauntedmc.serverfeatures.internal.cache;

import java.util.Map;
import java.util.Objects;

/**
 * A single cached item: an arbitrary Map of values plus its expiration timestamp.
 */
public final class CacheValue {
    private final Map<String, Object> data;
    private final long expirationTimestamp;

    public CacheValue(Map<String, Object> data, long expirationTimestamp) {
        this.data = Objects.requireNonNull(data, "data");
        this.expirationTimestamp = expirationTimestamp;
    }

    /** The original key→value map you stored. */
    public Map<String, Object> getData() {
        return data;
    }

    /** Epoch millis when this entry expires (inclusive). */
    public long getExpirationTimestamp() {
        return expirationTimestamp;
    }

    /** Whether this entry is now past its TTL. */
    public boolean isExpired() {
        return expirationTimestamp > 0 && System.currentTimeMillis() > expirationTimestamp;
    }
}
