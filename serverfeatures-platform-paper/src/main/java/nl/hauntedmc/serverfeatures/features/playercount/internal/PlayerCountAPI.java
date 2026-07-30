package nl.hauntedmc.serverfeatures.features.playercount.internal;

import java.util.Optional;

/**
 * Reusable backend API backed by the latest fresh proxy snapshot.
 */
public final class PlayerCountAPI {

    private final PlayerCountSnapshotStore store;

    public PlayerCountAPI(PlayerCountSnapshotStore store) {
        this.store = java.util.Objects.requireNonNull(store, "store");
    }

    public Optional<PlayerCountSnapshot> current() {
        return store.currentFresh(System.currentTimeMillis());
    }

    public Optional<PlayerCountSnapshot.Counts> network() {
        return store.network(System.currentTimeMillis());
    }

    public Optional<PlayerCountSnapshot.Counts> localServer() {
        return store.localServer(System.currentTimeMillis());
    }

    public Optional<PlayerCountSnapshot.Counts> server(String serverName) {
        return store.server(serverName, System.currentTimeMillis());
    }

    public boolean isAvailable() {
        return store.isAvailable(System.currentTimeMillis());
    }

    public boolean isStale() {
        return store.isStale(System.currentTimeMillis());
    }

    public long ageMillis() {
        return store.ageMillis(System.currentTimeMillis());
    }

    public long publishedAtEpochMillis() {
        return store.current().map(PlayerCountSnapshot::publishedAtEpochMillis).orElse(0L);
    }
}
