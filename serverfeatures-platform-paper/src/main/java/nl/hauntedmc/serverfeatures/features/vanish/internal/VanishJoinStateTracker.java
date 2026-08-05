package nl.hauntedmc.serverfeatures.features.vanish.internal;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fences asynchronous persisted-state restoration to one concrete player connection.
 */
final class VanishJoinStateTracker {
    private final AtomicLong generationSequence = new AtomicLong();
    private final Map<UUID, Resolution> resolutions = new ConcurrentHashMap<>();

    long begin(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        long generation = generationSequence.incrementAndGet();
        Resolution replacement = new Resolution(generation, new CompletableFuture<>());
        Resolution previous = resolutions.put(playerUuid, replacement);
        cancel(previous, "Superseded by a newer player connection.");
        return generation;
    }

    CompletionStage<Boolean> await(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Resolution resolution = resolutions.get(playerUuid);
        if (resolution == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "No active Vanish join-state resolution exists for " + playerUuid + "."
            ));
        }
        return resolution.future().minimalCompletionStage();
    }

    boolean isCurrent(UUID playerUuid, long generation) {
        Resolution resolution = resolutions.get(playerUuid);
        return resolution != null && resolution.generation() == generation;
    }

    boolean complete(UUID playerUuid, long generation, boolean vanished) {
        Resolution resolution = resolutions.get(playerUuid);
        return resolution != null
                && resolution.generation() == generation
                && resolution.future().complete(vanished);
    }

    boolean fail(UUID playerUuid, long generation, Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        Resolution resolution = resolutions.get(playerUuid);
        return resolution != null
                && resolution.generation() == generation
                && resolution.future().completeExceptionally(failure);
    }

    void override(UUID playerUuid, boolean vanished) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        long generation = generationSequence.incrementAndGet();
        Resolution replacement = new Resolution(
                generation,
                CompletableFuture.completedFuture(vanished)
        );
        resolutions.compute(playerUuid, (ignored, previous) -> {
            if (previous != null && !previous.future().isDone()) {
                previous.future().complete(vanished);
            }
            return replacement;
        });
    }

    void remove(UUID playerUuid) {
        cancel(resolutions.remove(playerUuid), "Player disconnected before Vanish restoration completed.");
    }

    void clear() {
        resolutions.values().forEach(resolution ->
                cancel(resolution, "Vanish was disabled before restoration completed."));
        resolutions.clear();
    }

    private static void cancel(Resolution resolution, String reason) {
        if (resolution != null && !resolution.future().isDone()) {
            resolution.future().completeExceptionally(new CancellationException(reason));
        }
    }

    private record Resolution(long generation, CompletableFuture<Boolean> future) { }
}
