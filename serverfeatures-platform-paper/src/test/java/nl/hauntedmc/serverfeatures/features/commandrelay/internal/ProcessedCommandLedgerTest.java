package nl.hauntedmc.serverfeatures.features.commandrelay.internal;

import nl.hauntedmc.serverfeatures.api.io.cache.CacheValue;
import nl.hauntedmc.serverfeatures.api.io.cache.FileCacheStore;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ProcessedCommandLedgerTest {

    @Test
    void reloadsAndPersistsMarkers() {
        FileCacheStore store = mock(FileCacheStore.class);
        when(store.listAll()).thenReturn(Map.of(
                "command.existing",
                CacheValue.builder(60_000L).with("processed", true).build()
        ));
        ProcessedCommandLedger ledger = new ProcessedCommandLedger(store, 120_000L);

        assertTrue(ledger.isProcessed("command.existing"));
        assertFalse(ledger.isProcessed("command.new"));
        ledger.markProcessed("command.new");
        assertTrue(ledger.isProcessed("command.new"));
        verify(store).put(eq("command.new"), any(CacheValue.class));
    }

    @Test
    void rejectsNonPositiveMarkerTtl() {
        FileCacheStore store = mock(FileCacheStore.class);
        when(store.listAll()).thenReturn(Map.of());

        assertThrows(IllegalArgumentException.class, () -> new ProcessedCommandLedger(store, 0L));
    }
}
