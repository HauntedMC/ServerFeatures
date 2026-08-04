package nl.hauntedmc.serverfeatures.features.capacity.internal;

import java.util.Map;
import java.util.Optional;

/** Immutable validated latest-value Capacity snapshot. */
public record CapacitySnapshot(
        String publisherId,
        String publisherEpoch,
        long sequence,
        long publishedAtEpochMillis,
        long receivedAtEpochMillis,
        int activeLeases,
        Scope proxy,
        Scope gameplay,
        Map<String, Scope> groups,
        Map<String, Scope> servers
) {
    public CapacitySnapshot {
        groups = groups == null ? Map.of() : Map.copyOf(groups);
        servers = servers == null ? Map.of() : Map.copyOf(servers);
    }

    public Optional<Scope> findGroup(String groupName) {
        return Optional.ofNullable(groups.get(groupName));
    }

    public Optional<Scope> findServer(String serverName) {
        return Optional.ofNullable(servers.get(serverName));
    }

    public record Scope(
            String name,
            int capacity,
            int reservedSlots,
            int occupied,
            int pending,
            int restorationReserved,
            State state
    ) {
        public int effectiveUsed() {
            long used = (long) occupied + pending + restorationReserved;
            return used >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, used);
        }

        public int normalCapacity() {
            return Math.max(0, capacity - reservedSlots);
        }

        public int normalAvailable() {
            return Math.max(0, normalCapacity() - effectiveUsed());
        }

        public int absoluteAvailable() {
            return Math.max(0, capacity - effectiveUsed());
        }

        public boolean isLimited() {
            return capacity > 0;
        }

        public boolean isOpen() {
            return state == State.OPEN;
        }

        public boolean isAcceptingNormalPlayers() {
            return isOpen() && (!isLimited() || normalAvailable() > 0);
        }
    }

    public enum State {
        OPEN,
        DRAINING,
        CLOSED,
        OFFLINE
    }
}
