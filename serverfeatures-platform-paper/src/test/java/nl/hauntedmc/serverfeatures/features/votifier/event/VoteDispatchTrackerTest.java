package nl.hauntedmc.serverfeatures.features.votifier.event;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoteDispatchTrackerTest {

    @Test
    void exposesProcessingKeyAndTrackedWorkDuringDispatchOnly() {
        CompletableFuture<Void> processing = new CompletableFuture<>();
        CompletableFuture<Void> tracked;

        try (VoteDispatchTracker tracker = VoteDispatchTracker.open("vote.123")) {
            assertEquals("vote.123", VoteDispatchTracker.currentProcessingKey().orElseThrow());
            VoteDispatchTracker.trackCurrent(processing);
            tracked = tracker.processingCompletion().toCompletableFuture();
        }

        assertTrue(VoteDispatchTracker.currentProcessingKey().isEmpty());
        assertFalse(tracked.isDone());
        processing.complete(null);
        assertTrue(tracked.isDone());
    }
}
