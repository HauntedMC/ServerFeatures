package nl.hauntedmc.serverfeatures.features.playercount.internal;

import com.google.gson.Gson;
import nl.hauntedmc.serverfeatures.features.playercount.messaging.PlayerCountSnapshotMessage;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerCountSnapshotStoreTest {

    @Test
    void appliesLatestValidSnapshotAndExpiresIt() {
        PlayerCountSnapshotStore store = new PlayerCountSnapshotStore(
                "Survival",
                5_000L,
                "proxy-1"
        );
        PlayerCountSnapshotMessage message = message(
                "proxy-1",
                "epoch-1",
                1L,
                10_000L,
                8,
                2,
                Map.of("survival", new Counts(5, 1))
        );

        assertEquals(PlayerCountSnapshotStore.ApplyResult.APPLIED, store.apply(message, 20_000L));
        assertEquals(
                new PlayerCountSnapshot.Counts(8, 2),
                store.network(24_000L).orElseThrow()
        );
        assertEquals(
                new PlayerCountSnapshot.Counts(5, 1),
                store.localServer(24_000L).orElseThrow()
        );
        assertTrue(store.isAvailable(25_000L));
        assertFalse(store.isAvailable(25_001L));
        assertTrue(store.isStale(25_001L));
    }

    @Test
    void rejectsOlderDuplicateAndCorruptSnapshots() {
        PlayerCountSnapshotStore store = new PlayerCountSnapshotStore(
                "survival",
                5_000L,
                "proxy-1"
        );
        assertEquals(
                PlayerCountSnapshotStore.ApplyResult.APPLIED,
                store.apply(message("proxy-1", "epoch-1", 2L, 20_000L, 4, 0, Map.of()), 30_000L)
        );
        assertEquals(
                PlayerCountSnapshotStore.ApplyResult.STALE,
                store.apply(message("proxy-1", "epoch-1", 1L, 19_000L, 3, 0, Map.of()), 30_001L)
        );
        assertEquals(
                PlayerCountSnapshotStore.ApplyResult.STALE,
                store.apply(message("proxy-1", "epoch-1", 2L, 20_000L, 4, 0, Map.of()), 30_002L)
        );
        assertEquals(
                PlayerCountSnapshotStore.ApplyResult.INVALID,
                store.apply(
                        message(
                                "proxy-1",
                                "epoch-1",
                                3L,
                                21_000L,
                                1,
                                0,
                                Map.of("survival", new Counts(2, 0))
                        ),
                        30_003L
                )
        );
        assertEquals(4, store.current().orElseThrow().network().online());
    }

    @Test
    void rejectsUnexpectedPublisherAndRetiredEpochs() {
        PlayerCountSnapshotStore store = new PlayerCountSnapshotStore(
                "survival",
                5_000L,
                "proxy-1"
        );
        assertEquals(
                PlayerCountSnapshotStore.ApplyResult.INVALID,
                store.apply(message("proxy-2", "epoch-x", 1L, 10_000L, 1, 0, Map.of()), 20_000L)
        );
        assertEquals(
                PlayerCountSnapshotStore.ApplyResult.APPLIED,
                store.apply(message("proxy-1", "epoch-1", 5L, 20_000L, 4, 0, Map.of()), 30_000L)
        );
        assertEquals(
                PlayerCountSnapshotStore.ApplyResult.APPLIED,
                store.apply(message("proxy-1", "epoch-2", 1L, 21_000L, 3, 0, Map.of()), 30_001L)
        );
        assertEquals(
                PlayerCountSnapshotStore.ApplyResult.STALE,
                store.apply(message("proxy-1", "epoch-1", 6L, 22_000L, 5, 0, Map.of()), 30_002L)
        );
        assertEquals("epoch-2", store.current().orElseThrow().publisherEpoch());
    }

    private static PlayerCountSnapshotMessage message(
            String publisherId,
            String publisherEpoch,
            long sequence,
            long publishedAt,
            int online,
            int vanished,
            Map<String, Counts> servers
    ) {
        Gson gson = new Gson();
        return gson.fromJson(
                gson.toJson(new Payload(
                        PlayerCountSnapshotMessage.SCHEMA_VERSION,
                        publisherId,
                        publisherEpoch,
                        sequence,
                        publishedAt,
                        online,
                        vanished,
                        servers
                )),
                PlayerCountSnapshotMessage.class
        );
    }

    private record Counts(int online, int vanished) {
    }

    private record Payload(
            int schemaVersion,
            String publisherId,
            String publisherEpoch,
            long sequence,
            long publishedAtEpochMillis,
            int networkOnline,
            int networkVanished,
            Map<String, Counts> servers
    ) {
    }
}
