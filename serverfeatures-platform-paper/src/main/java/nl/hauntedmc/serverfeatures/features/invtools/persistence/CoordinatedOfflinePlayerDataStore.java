package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import nl.hauntedmc.serverfeatures.features.invtools.model.InventoryKind;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventorySnapshot;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps one target's complete offline storage transaction serialized and visible to the login fence.
 *
 * <p>The underlying NBT store has its own narrow file lock and observer calls. This outer boundary is
 * deliberately broader: it remains active while ordinary edit verification, recovery-backup cleanup,
 * and any rollback performed by decorators are still running. Nested observer counts are supported by
 * the migration coordinator and ensure login cannot begin between atomic replacement and verification.</p>
 */
public final class CoordinatedOfflinePlayerDataStore implements OfflinePlayerDataStore {

    private static final int PLAYER_LOCK_COUNT = 64;

    private final OfflinePlayerDataStore delegate;
    private final PlayerDataMigrationObserver observer;
    private final Object[] playerLocks = createPlayerLocks();

    public CoordinatedOfflinePlayerDataStore(
            OfflinePlayerDataStore delegate,
            PlayerDataMigrationObserver observer
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.observer = Objects.requireNonNull(observer, "observer");
    }

    @Override
    public boolean hasPlayerData(UUID playerId) throws IOException {
        return delegate.hasPlayerData(Objects.requireNonNull(playerId, "playerId"));
    }

    @Override
    public OfflinePlayerData load(UUID playerId) throws IOException {
        UUID checkedId = Objects.requireNonNull(playerId, "playerId");
        observer.operationStarted(checkedId);
        try {
            synchronized (playerLock(checkedId)) {
                return delegate.load(checkedId);
            }
        } finally {
            observer.operationFinished(checkedId);
        }
    }

    @Override
    public Optional<UUID> resolvePlayerId(
            Optional<UUID> preferredPlayerId,
            String playerName
    ) throws IOException {
        return delegate.resolvePlayerId(preferredPlayerId, playerName);
    }

    @Override
    public void rememberPlayerIdentity(UUID playerId, String playerName) {
        delegate.rememberPlayerIdentity(playerId, playerName);
    }

    @Override
    public void save(
            OfflinePlayerData original,
            InventoryKind kind,
            InventorySnapshot changedSnapshot
    ) throws IOException {
        OfflinePlayerData checkedOriginal = Objects.requireNonNull(original, "original");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(changedSnapshot, "changedSnapshot");
        UUID playerId = checkedOriginal.playerId();
        observer.operationStarted(playerId);
        try {
            synchronized (playerLock(playerId)) {
                delegate.save(checkedOriginal, kind, changedSnapshot);
            }
        } finally {
            observer.operationFinished(playerId);
        }
    }

    private Object playerLock(UUID playerId) {
        int index = Math.floorMod(playerId.hashCode(), playerLocks.length);
        return playerLocks[index];
    }

    private static Object[] createPlayerLocks() {
        Object[] locks = new Object[PLAYER_LOCK_COUNT];
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new Object();
        }
        return locks;
    }
}
