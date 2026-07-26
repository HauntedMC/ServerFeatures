package nl.hauntedmc.serverfeatures.features.commandrelay.internal;

import nl.hauntedmc.serverfeatures.api.io.cache.CacheValue;
import nl.hauntedmc.serverfeatures.api.io.cache.FileCacheStore;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent replay ledger for completed command relay operations.
 */
final class ProcessedCommandLedger {

    private final FileCacheStore store;
    private final long markerTtlMillis;
    private final Set<String> processedKeys = ConcurrentHashMap.newKeySet();

    ProcessedCommandLedger(FileCacheStore store, long markerTtlMillis) {
        this.store = Objects.requireNonNull(store, "store");
        if (markerTtlMillis <= 0L) {
            throw new IllegalArgumentException("markerTtlMillis must be positive");
        }
        this.markerTtlMillis = markerTtlMillis;
        this.processedKeys.addAll(store.listAll().keySet());
    }

    boolean isProcessed(String processingKey) {
        return processedKeys.contains(processingKey);
    }

    void markProcessed(String processingKey) {
        CacheValue marker = CacheValue.builder(markerTtlMillis)
                .with("processed", true)
                .build();
        synchronized (store) {
            store.put(processingKey, marker);
        }
        processedKeys.add(processingKey);
    }
}
