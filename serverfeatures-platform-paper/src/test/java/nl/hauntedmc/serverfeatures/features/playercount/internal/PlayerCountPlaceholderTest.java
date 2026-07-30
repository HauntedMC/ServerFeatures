package nl.hauntedmc.serverfeatures.features.playercount.internal;

import com.google.gson.Gson;
import nl.hauntedmc.serverfeatures.features.playercount.messaging.PlayerCountSnapshotMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlayerCountPlaceholderTest {

    @Test
    void exposesNetworkLocalAndNamedServerCounts() {
        long now = System.currentTimeMillis();
        PlayerCountSnapshotStore store = new PlayerCountSnapshotStore(
                "survival",
                60_000L,
                "proxy-1"
        );
        store.apply(message(now), now);
        PlayerCountPlaceholder placeholder = new PlayerCountPlaceholder(new PlayerCountAPI(store));

        assertEquals("10", placeholder.onRequest(null, "network_online"));
        assertEquals("7", placeholder.onRequest(null, "network_visible"));
        assertEquals("3", placeholder.onRequest(null, "network_vanished"));
        assertEquals("6", placeholder.onRequest(null, "server_online"));
        assertEquals("4", placeholder.onRequest(null, "server_visible"));
        assertEquals("1", placeholder.onRequest(null, "server_lobby_1_vanished"));
        assertEquals("true", placeholder.onRequest(null, "available"));
        assertEquals("false", placeholder.onRequest(null, "stale"));
        assertNull(placeholder.onRequest(null, "unknown"));
    }

    @Test
    void returnsZeroCountsUntilAFreshSnapshotExists() {
        PlayerCountSnapshotStore store = new PlayerCountSnapshotStore(
                "survival",
                1L,
                "proxy-1"
        );
        PlayerCountPlaceholder placeholder = new PlayerCountPlaceholder(new PlayerCountAPI(store));

        assertEquals("0", placeholder.onRequest(null, "network_online"));
        assertEquals("0", placeholder.onRequest(null, "server_survival_visible"));
        assertEquals("false", placeholder.onRequest(null, "available"));
        assertEquals("-1", placeholder.onRequest(null, "age_seconds"));
    }

    private static PlayerCountSnapshotMessage message(long publishedAt) {
        String json = """
                {
                  "schemaVersion": 1,
                  "publisherId": "proxy-1",
                  "publisherEpoch": "epoch-1",
                  "sequence": 1,
                  "publishedAtEpochMillis": %d,
                  "networkOnline": 10,
                  "networkVanished": 3,
                  "servers": {
                    "survival": {"online": 6, "vanished": 2},
                    "lobby_1": {"online": 4, "vanished": 1}
                  }
                }
                """.formatted(publishedAt);
        return new Gson().fromJson(json, PlayerCountSnapshotMessage.class);
    }
}
