package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import nl.hauntedmc.serverfeatures.features.invtools.model.InventoryKind;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventorySnapshot;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Prevents new playerdata operations once InvTools begins shutting down and waits for every operation
 * that already crossed the gate. This keeps feature reload/disable from removing login protection
 * while a queued migration, recovery, or save is still touching disk.
 */
public final class ManagedOfflinePlayerDataStore implements OfflinePlayerDataStore {

    private final OfflinePlayerDataStore delegate;
    private final ReentrantLock lifecycleLock = new ReentrantLock(true);
    private final Condition idle = lifecycleLock.newCondition();

    private boolean accepting = true;
    private int activeOperations;

    public ManagedOfflinePlayerDataStore(OfflinePlayerDataStore delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public boolean hasPlayerData(UUID playerId) throws IOException {
        return execute(() -> delegate.hasPlayerData(playerId));
    }

    @Override
    public OfflinePlayerData load(UUID playerId) throws IOException {
        return execute(() -> delegate.load(playerId));
    }

    @Override
    public Optional<UUID> resolvePlayerId(
            Optional<UUID> preferredPlayerId,
            String playerName
    ) throws IOException {
        return execute(() -> delegate.resolvePlayerId(preferredPlayerId, playerName));
    }

    @Override
    public void rememberPlayerIdentity(UUID playerId, String playerName) {
        lifecycleLock.lock();
        try {
            if (!accepting) {
                return;
            }
            delegate.rememberPlayerIdentity(playerId, playerName);
        } finally {
            lifecycleLock.unlock();
        }
    }

    @Override
    public void save(
            OfflinePlayerData original,
            InventoryKind kind,
            InventorySnapshot changedSnapshot
    ) throws IOException {
        execute(() -> {
            delegate.save(original, kind, changedSnapshot);
            return null;
        });
    }

    /**
     * Closes the operation gate and waits without a timeout. Playerdata work is locally bounded by
     * InvTools' file-size limits and must reach a real terminal state; abandoning an in-flight atomic
     * replacement merely to make feature shutdown faster would be less safe.
     */
    public void closeAndAwait() {
        boolean interrupted = false;
        lifecycleLock.lock();
        try {
            accepting = false;
            while (activeOperations > 0) {
                try {
                    idle.await();
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
        } finally {
            lifecycleLock.unlock();
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    boolean acceptingOperations() {
        lifecycleLock.lock();
        try {
            return accepting;
        } finally {
            lifecycleLock.unlock();
        }
    }

    int activeOperationCount() {
        lifecycleLock.lock();
        try {
            return activeOperations;
        } finally {
            lifecycleLock.unlock();
        }
    }

    private <T> T execute(IoOperation<T> operation) throws IOException {
        enter();
        try {
            return operation.run();
        } finally {
            leave();
        }
    }

    private void enter() throws IOException {
        lifecycleLock.lock();
        try {
            if (!accepting) {
                throw new IOException("InvTools playerdata storage is shutting down");
            }
            activeOperations++;
        } finally {
            lifecycleLock.unlock();
        }
    }

    private void leave() {
        lifecycleLock.lock();
        try {
            if (activeOperations <= 0) {
                throw new IllegalStateException("InvTools playerdata operation count underflow");
            }
            activeOperations--;
            if (activeOperations == 0) {
                idle.signalAll();
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    @FunctionalInterface
    private interface IoOperation<T> {
        T run() throws IOException;
    }
}
