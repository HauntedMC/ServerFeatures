package nl.hauntedmc.serverfeatures.features.capacity.messaging;

import nl.hauntedmc.dataprovider.database.messaging.api.AbstractEventMessage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Backend decoder for the versioned Capacity snapshot wire contract.
 *
 * <p>The producer contract lives in ProxyFeatures contracts. This local decoder depends only on
 * the stable JSON wire shape so the two plugins can be released independently.</p>
 */
public final class CapacitySnapshotMessage extends AbstractEventMessage {

    public static final String TYPE = "capacity_snapshot";
    public static final int SCHEMA_VERSION = 1;

    private int schemaVersion;
    private String publisherId;
    private String publisherEpoch;
    private long sequence;
    private long publishedAtEpochMillis;
    private int activeLeases;
    private Scope proxy;
    private Scope gameplay;
    private Map<String, Scope> groups;
    private Map<String, Scope> servers;

    @SuppressWarnings("unused")
    private CapacitySnapshotMessage() {
        super(TYPE);
        this.schemaVersion = 0;
        this.groups = Map.of();
        this.servers = Map.of();
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getPublisherId() {
        return publisherId;
    }

    public String getPublisherEpoch() {
        return publisherEpoch;
    }

    public long getSequence() {
        return sequence;
    }

    public long getPublishedAtEpochMillis() {
        return publishedAtEpochMillis;
    }

    public int getActiveLeases() {
        return activeLeases;
    }

    public Scope getProxy() {
        return proxy;
    }

    public Scope getGameplay() {
        return gameplay;
    }

    public Map<String, Scope> getGroups() {
        return immutableCopy(groups);
    }

    public Map<String, Scope> getServers() {
        return immutableCopy(servers);
    }

    public static String normalizeScopeName(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static Map<String, Scope> immutableCopy(Map<String, Scope> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(scopes));
    }

    public static final class Scope {
        private String name;
        private int capacity;
        private int reservedSlots;
        private int occupied;
        private int pending;
        private int restorationReserved;
        private String state;

        @SuppressWarnings("unused")
        private Scope() {
        }

        public String getName() {
            return name;
        }

        public int getCapacity() {
            return capacity;
        }

        public int getReservedSlots() {
            return reservedSlots;
        }

        public int getOccupied() {
            return occupied;
        }

        public int getPending() {
            return pending;
        }

        public int getRestorationReserved() {
            return restorationReserved;
        }

        public String getState() {
            return state;
        }
    }
}
