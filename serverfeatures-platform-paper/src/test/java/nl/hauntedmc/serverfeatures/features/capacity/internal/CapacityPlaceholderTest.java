package nl.hauntedmc.serverfeatures.features.capacity.internal;

import com.google.gson.Gson;
import nl.hauntedmc.serverfeatures.features.capacity.messaging.CapacitySnapshotMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CapacityPlaceholderTest {

    @Test
    void exposesNetworkGameplayGroupLocalAndNamedServerScopes() {
        long now = System.currentTimeMillis();
        CapacitySnapshotStore store = new CapacitySnapshotStore(
                "survival_1",
                60_000L,
                "proxy-1"
        );
        store.apply(message(now), now);
        CapacityPlaceholder placeholder = new CapacityPlaceholder(new CapacityAPI(store));

        assertEquals("600", placeholder.onRequest(null, "network_capacity"));
        assertEquals("468", placeholder.onRequest(null, "network_available"));
        assertEquals("478", placeholder.onRequest(null, "network_absolute_available"));
        assertEquals("open", placeholder.onRequest(null, "network_state"));
        assertEquals("true", placeholder.onRequest(null, "network_accepting"));

        assertEquals("407", placeholder.onRequest(null, "gameplay_available"));
        assertEquals("55", placeholder.onRequest(null, "group_survival_used"));
        assertEquals("40", placeholder.onRequest(null, "group_survival_available"));
        assertEquals("false", placeholder.onRequest(null, "group_survival_accepting"));
        assertEquals("draining", placeholder.onRequest(null, "group_survival_state"));

        assertEquals("32", placeholder.onRequest(null, "server_available"));
        assertEquals("35", placeholder.onRequest(null, "server_absolute_available"));
        assertEquals("true", placeholder.onRequest(null, "server_exists"));
        assertEquals("32", placeholder.onRequest(null, "server_survival_1_normal_available"));
        assertEquals("3", placeholder.onRequest(null, "server_survival_1_reserved_available"));

        assertEquals("false", placeholder.onRequest(null, "server_missing_exists"));
        assertEquals("0", placeholder.onRequest(null, "server_missing_available"));
        assertEquals("unavailable", placeholder.onRequest(null, "server_missing_state"));

        assertEquals("false", placeholder.onRequest(null, "server_unlimited_limited"));
        assertEquals("false", placeholder.onRequest(null, "server_unlimited_full"));
        assertEquals("true", placeholder.onRequest(null, "server_unlimited_accepting"));

        assertEquals("4", placeholder.onRequest(null, "active_leases"));
        assertEquals("true", placeholder.onRequest(null, "available"));
        assertEquals("false", placeholder.onRequest(null, "stale"));
        assertNull(placeholder.onRequest(null, "unknown"));
    }

    @Test
    void returnsSafeValuesUntilAFreshSnapshotExists() {
        CapacitySnapshotStore store = new CapacitySnapshotStore(
                "survival",
                1L,
                "proxy-1"
        );
        CapacityPlaceholder placeholder = new CapacityPlaceholder(new CapacityAPI(store));

        assertEquals("0", placeholder.onRequest(null, "network_capacity"));
        assertEquals("0", placeholder.onRequest(null, "server_available"));
        assertEquals("false", placeholder.onRequest(null, "server_exists"));
        assertEquals("false", placeholder.onRequest(null, "group_survival_exists"));
        assertEquals("unavailable", placeholder.onRequest(null, "gameplay_state"));
        assertEquals("false", placeholder.onRequest(null, "available"));
        assertEquals("-1", placeholder.onRequest(null, "age_seconds"));
    }

    private static CapacitySnapshotMessage message(long publishedAt) {
        String json = """
                {
                  "schemaVersion": 1,
                  "publisherId": "proxy-1",
                  "publisherEpoch": "epoch-1",
                  "sequence": 1,
                  "publishedAtEpochMillis": %d,
                  "activeLeases": 4,
                  "proxy": {
                    "name": "proxy",
                    "capacity": 600,
                    "reservedSlots": 10,
                    "occupied": 120,
                    "pending": 2,
                    "restorationReserved": 0,
                    "state": "OPEN"
                  },
                  "gameplay": {
                    "name": "gameplay",
                    "capacity": 500,
                    "reservedSlots": 10,
                    "occupied": 80,
                    "pending": 2,
                    "restorationReserved": 1,
                    "state": "OPEN"
                  },
                  "groups": {
                    "survival": {
                      "name": "survival",
                      "capacity": 100,
                      "reservedSlots": 5,
                      "occupied": 50,
                      "pending": 3,
                      "restorationReserved": 2,
                      "state": "DRAINING"
                    }
                  },
                  "servers": {
                    "survival_1": {
                      "name": "survival_1",
                      "capacity": 60,
                      "reservedSlots": 3,
                      "occupied": 24,
                      "pending": 1,
                      "restorationReserved": 0,
                      "state": "OPEN"
                    },
                    "unlimited": {
                      "name": "unlimited",
                      "capacity": 0,
                      "reservedSlots": 0,
                      "occupied": 10,
                      "pending": 0,
                      "restorationReserved": 0,
                      "state": "OPEN"
                    }
                  }
                }
                """.formatted(publishedAt);
        return new Gson().fromJson(json, CapacitySnapshotMessage.class);
    }
}
