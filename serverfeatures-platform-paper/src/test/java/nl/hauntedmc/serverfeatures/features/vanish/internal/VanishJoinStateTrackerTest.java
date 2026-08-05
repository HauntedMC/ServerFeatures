package nl.hauntedmc.serverfeatures.features.vanish.internal;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanishJoinStateTrackerTest {

    @Test
    void completesTheCurrentConnectionAndRetainsItsResult() {
        VanishJoinStateTracker tracker = new VanishJoinStateTracker();
        UUID playerUuid = UUID.randomUUID();
        long generation = tracker.begin(playerUuid);
        CompletionStage<Boolean> result = tracker.await(playerUuid);

        assertTrue(tracker.isCurrent(playerUuid, generation));
        assertTrue(tracker.complete(playerUuid, generation, true));
        assertTrue(result.toCompletableFuture().join());
        assertTrue(tracker.await(playerUuid).toCompletableFuture().join());
        assertFalse(tracker.complete(playerUuid, generation, false));
    }

    @Test
    void newerConnectionCancelsThePreviousResultAndRejectsStaleCompletion() {
        VanishJoinStateTracker tracker = new VanishJoinStateTracker();
        UUID playerUuid = UUID.randomUUID();
        long oldGeneration = tracker.begin(playerUuid);
        CompletionStage<Boolean> oldResult = tracker.await(playerUuid);

        long newGeneration = tracker.begin(playerUuid);
        CompletionStage<Boolean> newResult = tracker.await(playerUuid);

        assertCancelled(oldResult);
        assertFalse(tracker.isCurrent(playerUuid, oldGeneration));
        assertFalse(tracker.complete(playerUuid, oldGeneration, true));
        assertTrue(tracker.complete(playerUuid, newGeneration, false));
        assertFalse(newResult.toCompletableFuture().join());
    }

    @Test
    void disconnectCancelsPendingResolutionAndRemovesIt() {
        VanishJoinStateTracker tracker = new VanishJoinStateTracker();
        UUID playerUuid = UUID.randomUUID();
        tracker.begin(playerUuid);
        CompletionStage<Boolean> result = tracker.await(playerUuid);

        tracker.remove(playerUuid);

        assertCancelled(result);
        assertThrows(CompletionException.class, () -> tracker.await(playerUuid).toCompletableFuture().join());
    }

    @Test
    void explicitStateOverrideCompletesPendingWaitersAndFencesTheDatabaseResult() {
        VanishJoinStateTracker tracker = new VanishJoinStateTracker();
        UUID playerUuid = UUID.randomUUID();
        long databaseGeneration = tracker.begin(playerUuid);
        CompletionStage<Boolean> pendingResult = tracker.await(playerUuid);

        tracker.override(playerUuid, true);

        assertTrue(pendingResult.toCompletableFuture().join());
        assertTrue(tracker.await(playerUuid).toCompletableFuture().join());
        assertFalse(tracker.isCurrent(playerUuid, databaseGeneration));
        assertFalse(tracker.complete(playerUuid, databaseGeneration, false));
    }

    @Test
    void failuresAreDeliveredOnlyToTheCurrentConnection() {
        VanishJoinStateTracker tracker = new VanishJoinStateTracker();
        UUID playerUuid = UUID.randomUUID();
        long generation = tracker.begin(playerUuid);
        CompletionStage<Boolean> result = tracker.await(playerUuid);
        IllegalStateException failure = new IllegalStateException("database unavailable");

        assertTrue(tracker.fail(playerUuid, generation, failure));
        CompletionException thrown = assertThrows(
                CompletionException.class,
                () -> result.toCompletableFuture().join()
        );
        assertEquals(failure, thrown.getCause());
    }

    private static void assertCancelled(CompletionStage<Boolean> stage) {
        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> stage.toCompletableFuture().join()
        );
        assertTrue(thrown instanceof CancellationException
                || thrown.getCause() instanceof CancellationException);
    }
}
