package nl.hauntedmc.serverfeatures.features.capacity.internal;

import java.util.Optional;

/** Reusable backend API backed by the latest fresh authoritative Capacity snapshot. */
public final class CapacityAPI {

    private final CapacitySnapshotStore store;

    public CapacityAPI(CapacitySnapshotStore store) {
        this.store = java.util.Objects.requireNonNull(store, "store");
    }

    public Optional<CapacitySnapshot> current() {
        return store.currentFresh(System.currentTimeMillis());
    }

    public Optional<CapacitySnapshot.Scope> network() {
        return store.proxy(System.currentTimeMillis());
    }

    public Optional<CapacitySnapshot.Scope> gameplay() {
        return store.gameplay(System.currentTimeMillis());
    }

    public Optional<CapacitySnapshot.Scope> group(String groupName) {
        return store.group(groupName, System.currentTimeMillis());
    }

    public Optional<CapacitySnapshot.Scope> localServer() {
        return store.localServer(System.currentTimeMillis());
    }

    public Optional<CapacitySnapshot.Scope> server(String serverName) {
        return store.server(serverName, System.currentTimeMillis());
    }

    public boolean isAvailable() {
        return store.isAvailable(System.currentTimeMillis());
    }

    public boolean isGroupAvailable(String groupName) {
        return store.isGroupAvailable(groupName, System.currentTimeMillis());
    }

    public boolean isLocalServerAvailable() {
        return store.isLocalServerAvailable(System.currentTimeMillis());
    }

    public boolean isServerAvailable(String serverName) {
        return store.isServerAvailable(serverName, System.currentTimeMillis());
    }

    public boolean isStale() {
        return store.isStale(System.currentTimeMillis());
    }

    public long ageMillis() {
        return store.ageMillis(System.currentTimeMillis());
    }

    public long publishedAtEpochMillis() {
        return store.current().map(CapacitySnapshot::publishedAtEpochMillis).orElse(0L);
    }

    public int activeLeases() {
        return current().map(CapacitySnapshot::activeLeases).orElse(0);
    }
}
