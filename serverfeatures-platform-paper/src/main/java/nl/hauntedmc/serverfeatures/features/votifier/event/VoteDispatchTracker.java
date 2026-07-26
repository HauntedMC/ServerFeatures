package nl.hauntedmc.serverfeatures.features.votifier.event;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Carries durable vote processing state through a synchronous external Votifier event dispatch.
 */
public final class VoteDispatchTracker implements AutoCloseable {

    private static final ThreadLocal<VoteDispatchTracker> ACTIVE = new ThreadLocal<>();

    private final String processingKey;
    private CompletionStage<Void> completion = CompletableFuture.completedFuture(null);
    private boolean closed;

    private VoteDispatchTracker(String processingKey) {
        this.processingKey = Objects.requireNonNull(processingKey, "processingKey");
    }

    public static VoteDispatchTracker open(String processingKey) {
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("A durable vote dispatch is already active on this thread.");
        }
        VoteDispatchTracker tracker = new VoteDispatchTracker(processingKey);
        ACTIVE.set(tracker);
        return tracker;
    }

    public static Optional<String> currentProcessingKey() {
        VoteDispatchTracker tracker = ACTIVE.get();
        return tracker == null ? Optional.empty() : Optional.of(tracker.processingKey);
    }

    public static void trackCurrent(CompletionStage<?> processing) {
        VoteDispatchTracker tracker = ACTIVE.get();
        if (tracker != null) {
            tracker.track(processing);
        }
    }

    private synchronized void track(CompletionStage<?> processing) {
        if (closed) {
            throw new IllegalStateException("The durable vote dispatch is already closed.");
        }
        Objects.requireNonNull(processing, "processing");
        completion = completion.thenCombine(processing, (ignored, result) -> null);
    }

    public synchronized CompletionStage<Void> processingCompletion() {
        return completion;
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            ACTIVE.remove();
        }
    }
}
