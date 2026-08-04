package nl.hauntedmc.serverfeatures.features.capacity.internal;

import com.google.gson.Gson;
import nl.hauntedmc.serverfeatures.features.capacity.messaging.CapacitySnapshotMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapacitySnapshotStoreTest {

    @Test
    void acceptsFreshOrderedSnapshotsAndRejectsDuplicates() {
        CapacitySnapshotStore store = new CapacitySnapshotStore(
                "survival",
                10_000L,
                "proxy-1"
        );

        assertEquals(
                CapacitySnapshotStore.ApplyResult.APPLIED,
                store.apply(message("proxy-1", "epoch-1", 1L, 1_000L, "OPEN"), 2_000L)
        );
        assertEquals(
                CapacitySnapshotStore.ApplyResult.STALE,
                store.apply(message("proxy-1", "epoch-1", 1L, 1_001L, "OPEN"), 2_001L)
        );
        assertEquals(
                CapacitySnapshotStore.ApplyResult.APPLIED,
                store.apply(message("proxy-1", "epoch-1", 2L, 1_002L, "DRAINING"), 2_002L)
        );

        assertEquals(
                CapacitySnapshot.State.DRAINING,
                store.localServer(2_003L).orElseThrow().state()
        );
    }

    @Test
    void rejectsWrongPublisherInvalidScopesAndRetiredEpochs() {
        CapacitySnapshotStore store = new CapacitySnapshotStore(
                "survival",
                10_000L,
                "proxy-1"
        );

        assertEquals(
                CapacitySnapshotStore.ApplyResult.INVALID,
                store.apply(message("other", "epoch-1", 1L, 1_000L, "OPEN"), 2_000L)
        );
        assertEquals(
                CapacitySnapshotStore.ApplyResult.APPLIED,
                store.apply(message("proxy-1", "epoch-1", 1L, 2_000L, "OPEN"), 2_000L)
        );
        assertEquals(
                CapacitySnapshotStore.ApplyResult.APPLIED,
                store.apply(message("proxy-1", "epoch-2", 1L, 3_000L, "OPEN"), 3_000L)
        );
        assertEquals(
                CapacitySnapshotStore.ApplyResult.STALE,
                store.apply(message("proxy-1", "epoch-1", 2L, 4_000L, "OPEN"), 4_000L)
        );

        CapacitySnapshotMessage invalid = new Gson().fromJson(
                validJson("proxy-1", "epoch-3", 1L, 5_000L, "OPEN")
                        .replace("\"reservedSlots\": 10", "\"reservedSlots\": 601"),
                CapacitySnapshotMessage.class
        );
        assertEquals(
                CapacitySnapshotStore.ApplyResult.INVALID,
                store.apply(invalid, 5_000L)
        );
    }

    @Test
    void expiresSnapshotsByReceiveTime() {
        CapacitySnapshotStore store = new CapacitySnapshotStore(
                "survival",
                1_000L,
                "proxy-1"
        );
        store.apply(message("proxy-1", "epoch-1", 1L, 1_000L, "OPEN"), 2_000L);

        assertTrue(store.isAvailable(3_000L));
        assertFalse(store.isAvailable(3_001L));
        assertTrue(store.isStale(3_001L));
        assertEquals(1_001L, store.ageMillis(3_001L));
    }

    private static CapacitySnapshotMessage message(
            String publisherId,
            String epoch,
            long sequence,
            long publishedAt,
            String state
    ) {
        return new Gson().fromJson(
                validJson(publisherId, epoch, sequence, publishedAt, state),
                CapacitySnapshotMessage.class
        );
    }

    private static String validJson(
            String publisherId,
            String epoch,
            long sequence,
            long publishedAt,
            String state
    ) {
        return """
                {
                  "schemaVersion": 1,
                  "publisherId": "%s",
                  "publisherEpoch": "%s",
                  "sequence": %d,
                  "publishedAtEpochMillis": %d,
                  "activeLeases": 1,
                  "proxy": {
                    "name": "proxy",
                    "capacity": 600,
                    "reservedSlots": 10,
                    "occupied": 100,
                    "pending": 1,
                    "restorationReserved": 0,
                    "state": "OPEN"
                  },
                  "gameplay": {
                    "name": "gameplay",
                    "capacity": 500,
                    "reservedSlots": 10,
                    "occupied": 80,
                    "pending": 1,
                    "restorationReserved": 0,
                    "state": "OPEN"
                  },
                  "groups": {},
                  "servers": {
                    "survival": {
                      "name": "survival",
                      "capacity": 100,
                      "reservedSlots": 10,
                      "occupied": 50,
                      "pending": 1,
                      "restorationReserved": 0,
                      "state": "%s"
                    }
                  }
                }
                """.formatted(publisherId, epoch, sequence, publishedAt, state);
    }
}
