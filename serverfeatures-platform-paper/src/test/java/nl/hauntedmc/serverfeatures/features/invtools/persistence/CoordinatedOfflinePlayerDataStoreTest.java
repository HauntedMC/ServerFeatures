package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import nl.hauntedmc.serverfeatures.features.invtools.model.InventoryKind;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventorySnapshot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CoordinatedOfflinePlayerDataStoreTest {

    private static final int PLAYER_LOCK_COUNT = 64;
    private static final PlayerDataRevision REVISION = new PlayerDataRevision("0".repeat(64));

    @Test
    void observerRemainsActiveAcrossCompleteSaveDecoratorWork() throws Exception {
        UUID playerId = UUID.randomUUID();
        AtomicInteger active = new AtomicInteger();
        CountDownLatch insideSave = new CountDownLatch(1);
        CountDownLatch releaseSave = new CountDownLatch(1);
        OfflinePlayerData original = new OfflinePlayerData(
                playerId,
                InventorySnapshot.empty(),
                REVISION
        );
        OfflinePlayerDataStore delegate = new StubStore() {
            @Override
            public void save(
                    OfflinePlayerData ignored,
                    InventoryKind kind,
                    InventorySnapshot changedSnapshot
            ) throws IOException {
                assertTrue(active.get() > 0);
                insideSave.countDown();
                await(releaseSave);
                assertTrue(active.get() > 0);
            }
        };
        CoordinatedOfflinePlayerDataStore store = new CoordinatedOfflinePlayerDataStore(
                delegate,
                countingObserver(active)
        );
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread worker = Thread.ofPlatform().start(() -> {
            try {
                store.save(original, InventoryKind.PLAYER, InventorySnapshot.empty());
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });

        assertTrue(insideSave.await(2, TimeUnit.SECONDS));
        assertEquals(1, active.get());
        releaseSave.countDown();
        worker.join(2_000L);

        assertFalse(worker.isAlive());
        assertEquals(0, active.get());
        if (failure.get() != null) {
            fail(failure.get());
        }
    }

    @Test
    void samePlayerOperationsAreSerializedWhileDifferentPlayersCanProceed() throws Exception {
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = differentStripe(firstPlayer);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondSamePlayerEntered = new CountDownLatch(1);
        CountDownLatch differentPlayerEntered = new CountDownLatch(1);
        AtomicInteger firstPlayerCalls = new AtomicInteger();
        OfflinePlayerDataStore delegate = new StubStore() {
            @Override
            public OfflinePlayerData load(UUID playerId) throws IOException {
                if (playerId.equals(firstPlayer)) {
                    int call = firstPlayerCalls.incrementAndGet();
                    if (call == 1) {
                        firstEntered.countDown();
                        await(releaseFirst);
                    } else {
                        secondSamePlayerEntered.countDown();
                    }
                } else {
                    differentPlayerEntered.countDown();
                }
                return new OfflinePlayerData(
                        playerId,
                        InventorySnapshot.empty(),
                        REVISION
                );
            }
        };
        CoordinatedOfflinePlayerDataStore store = new CoordinatedOfflinePlayerDataStore(
                delegate,
                PlayerDataMigrationObserver.NONE
        );

        Thread first = Thread.ofPlatform().start(() -> load(store, firstPlayer));
        assertTrue(firstEntered.await(2, TimeUnit.SECONDS));
        Thread samePlayer = Thread.ofPlatform().start(() -> load(store, firstPlayer));
        Thread differentPlayer = Thread.ofPlatform().start(() -> load(store, secondPlayer));

        assertTrue(differentPlayerEntered.await(2, TimeUnit.SECONDS));
        assertFalse(secondSamePlayerEntered.await(100, TimeUnit.MILLISECONDS));
        releaseFirst.countDown();

        first.join(2_000L);
        samePlayer.join(2_000L);
        differentPlayer.join(2_000L);
        assertTrue(secondSamePlayerEntered.await(2, TimeUnit.SECONDS));
    }

    @Test
    void operationFinishedRunsWhenDelegateFails() {
        UUID playerId = UUID.randomUUID();
        AtomicInteger active = new AtomicInteger();
        OfflinePlayerDataStore delegate = new StubStore() {
            @Override
            public OfflinePlayerData load(UUID ignored) throws IOException {
                throw new IOException("fixture failure");
            }
        };
        CoordinatedOfflinePlayerDataStore store = new CoordinatedOfflinePlayerDataStore(
                delegate,
                countingObserver(active)
        );

        try {
            store.load(playerId);
            fail("Expected load failure");
        } catch (IOException expected) {
            assertEquals("fixture failure", expected.getMessage());
        }

        assertEquals(0, active.get());
    }

    private static UUID differentStripe(UUID firstPlayer) {
        int firstStripe = stripe(firstPlayer);
        UUID candidate;
        do {
            candidate = UUID.randomUUID();
        } while (stripe(candidate) == firstStripe);
        return candidate;
    }

    private static int stripe(UUID playerId) {
        return Math.floorMod(playerId.hashCode(), PLAYER_LOCK_COUNT);
    }

    private static PlayerDataMigrationObserver countingObserver(AtomicInteger active) {
        return new PlayerDataMigrationObserver() {
            @Override
            public void operationStarted(UUID playerId) {
                active.incrementAndGet();
            }

            @Override
            public void operationFinished(UUID playerId) {
                active.decrementAndGet();
            }
        };
    }

    private static void load(CoordinatedOfflinePlayerDataStore store, UUID playerId) {
        try {
            store.load(playerId);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new IOException("fixture latch timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("fixture interrupted", exception);
        }
    }

    private abstract static class StubStore implements OfflinePlayerDataStore {
        @Override
        public boolean hasPlayerData(UUID playerId) {
            return true;
        }

        @Override
        public OfflinePlayerData load(UUID playerId) throws IOException {
            return new OfflinePlayerData(
                    playerId,
                    InventorySnapshot.empty(),
                    REVISION
            );
        }

        @Override
        public Optional<UUID> resolvePlayerId(
                Optional<UUID> preferredPlayerId,
                String playerName
        ) {
            return preferredPlayerId;
        }

        @Override
        public void rememberPlayerIdentity(UUID playerId, String playerName) {
        }

        @Override
        public void save(
                OfflinePlayerData original,
                InventoryKind kind,
                InventorySnapshot changedSnapshot
        ) throws IOException {
        }
    }
}
