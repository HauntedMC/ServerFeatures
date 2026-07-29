package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManagedOfflinePlayerDataStoreTest {

    @Test
    void closeWaitsForAnActivePlayerdataOperation() throws Exception {
        UUID playerId = UUID.randomUUID();
        OfflinePlayerDataStore delegate = mock(OfflinePlayerDataStore.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(delegate.load(playerId)).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return null;
        });
        ManagedOfflinePlayerDataStore store = new ManagedOfflinePlayerDataStore(delegate);

        CompletableFuture<Void> operation = CompletableFuture.runAsync(() -> {
            try {
                store.load(playerId);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        assertEquals(1, store.activeOperationCount());

        CompletableFuture<Void> close = CompletableFuture.runAsync(store::closeAndAwait);
        awaitCondition(() -> !store.acceptingOperations());
        assertFalse(close.isDone());

        release.countDown();
        operation.get(10, TimeUnit.SECONDS);
        close.get(10, TimeUnit.SECONDS);

        assertEquals(0, store.activeOperationCount());
    }

    @Test
    void operationsThatStartAfterCloseAreRejectedBeforeDelegateAccess() {
        OfflinePlayerDataStore delegate = mock(OfflinePlayerDataStore.class);
        ManagedOfflinePlayerDataStore store = new ManagedOfflinePlayerDataStore(delegate);
        store.closeAndAwait();

        IOException exception = assertThrows(
                IOException.class,
                () -> store.hasPlayerData(UUID.randomUUID())
        );

        assertTrue(exception.getMessage().contains("shutting down"));
    }

    private static void awaitCondition(Condition condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.evaluate() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertTrue(condition.evaluate());
    }

    @FunctionalInterface
    private interface Condition {
        boolean evaluate();
    }
}
