package nl.hauntedmc.serverfeatures.api.io.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A single cached item: your arbitrary key→value map plus its expiration timestamp.
 * Use the Builder to construct instances.
 */
public final class CacheValue {
    private final Map<String, Object> data;
    private final long expirationTimestamp;

    private CacheValue(Map<String, Object> data, long expirationTimestamp) {
        this.data = data;
        this.expirationTimestamp = expirationTimestamp;
    }

    /** Factory for implementations to rehydrate from disk. */
    public static CacheValue of(Map<String, Object> data, long expirationTimestamp) {
        Objects.requireNonNull(data, "data");
        return new CacheValue(Map.copyOf(data), expirationTimestamp);
    }


    /** The original key→value pairs you stored. */
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

    /** Start building a new CacheValue. */
    public static Builder builder(long ttlMillis) {
        return new Builder(ttlMillis);
    }

    public static final class Builder {
        private final Map<String, Object> data = new LinkedHashMap<>();
        private final long ttlMillis;

        /** @param ttlMillis how long from now (ms) until expiration. */
        private Builder(long ttlMillis) {
            if (ttlMillis < 0) throw new IllegalArgumentException("ttlMillis >= 0");
            this.ttlMillis = ttlMillis;
        }

        /** Add an arbitrary key→value to store. */
        public Builder with(String key, Object value) {
            Objects.requireNonNull(key, "key");
            data.put(key, value);
            return this;
        }

        /** Build the CacheValue, folding in the TTL. */
        public CacheValue build() {
            long expiresAt = System.currentTimeMillis() + ttlMillis;
            return new CacheValue(Map.copyOf(data), expiresAt);
        }
    }
}
