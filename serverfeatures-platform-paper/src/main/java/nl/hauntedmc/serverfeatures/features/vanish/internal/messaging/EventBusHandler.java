package nl.hauntedmc.serverfeatures.features.vanish.internal.messaging;

import nl.hauntedmc.dataprovider.database.messaging.durable.DurableEvent;
import nl.hauntedmc.dataprovider.database.messaging.durable.DurableMessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.durable.PublishedDurableEvent;
import nl.hauntedmc.proxyfeatures.contracts.messaging.VanishStateMessage;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.vanish.Vanish;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Publishes retry-safe, versioned vanish state transitions through Redis Streams.
 */
public class EventBusHandler {

    private final DurableMessagingDataAccess redisBus;
    private final Vanish feature;
    private final String serverName;
    private final String stream;
    private final int retryAttempts;
    private final long retryDelayMillis;
    private final AtomicLong stateVersion = new AtomicLong();

    public EventBusHandler(
            Vanish feature,
            DurableMessagingDataAccess redisBus,
            String serverName,
            String stream,
            int retryAttempts,
            long retryDelayMillis
    ) {
        this.feature = feature;
        this.redisBus = redisBus;
        this.serverName = serverName == null ? "" : serverName.trim();
        this.stream = stream;
        if (retryAttempts <= 0) {
            throw new IllegalArgumentException("retryAttempts must be positive");
        }
        if (retryDelayMillis <= 0L) {
            throw new IllegalArgumentException("retryDelayMillis must be positive");
        }
        this.retryAttempts = retryAttempts;
        this.retryDelayMillis = retryDelayMillis;
    }

    public CompletableFuture<PublishedDurableEvent> publishState(
            String playerUuid,
            String playerName,
            boolean vanished
    ) {
        UUID parsedUuid;
        try {
            parsedUuid = UUID.fromString(playerUuid);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("playerUuid must be a valid UUID", exception)
            );
        }

        VanishStateMessage message = new VanishStateMessage(
                parsedUuid.toString(),
                playerName == null ? "" : playerName,
                vanished,
                serverName,
                nextStateVersion()
        );
        DurableEvent<VanishStateMessage> event = new DurableEvent<>(
                message.getDurableKey(),
                message.getDurableKey(),
                message
        );
        CompletableFuture<PublishedDurableEvent> completion = new CompletableFuture<>();
        publishAttempt(event, playerName, 1, completion);
        return completion;
    }

    private void publishAttempt(
            DurableEvent<VanishStateMessage> event,
            String playerName,
            int attempt,
            CompletableFuture<PublishedDurableEvent> completion
    ) {
        CompletableFuture<PublishedDurableEvent> publication;
        try {
            publication = redisBus.publish(stream, event);
        } catch (RuntimeException exception) {
            publication = CompletableFuture.failedFuture(exception);
        }

        publication.whenComplete((published, throwable) -> {
            if (throwable == null) {
                completion.complete(published);
                return;
            }
            if (attempt >= retryAttempts) {
                feature.getLogger().severe(
                        "Failed to publish durable vanish update for " + playerName + " ("
                                + event.payload().getPlayerUuid() + ") after " + attempt
                                + " attempt(s): " + rootMessage(throwable)
                );
                completion.completeExceptionally(throwable);
                return;
            }

            feature.getLogger().warning(
                    "Retrying durable vanish update " + event.processingKey() + " after attempt "
                            + attempt + ": " + rootMessage(throwable)
            );
            try {
                feature.getLifecycleManager().getTaskManager().scheduleAsyncDelayedTask(
                        () -> publishAttempt(event, playerName, attempt + 1, completion),
                        BukkitTime.milliseconds(retryDelayMillis)
                );
            } catch (RuntimeException schedulingFailure) {
                feature.getLogger().severe(
                        "Could not schedule durable vanish retry " + event.processingKey() + ": "
                                + rootMessage(schedulingFailure)
                );
                completion.completeExceptionally(schedulingFailure);
            }
        });
    }

    private long nextStateVersion() {
        return stateVersion.updateAndGet(previous ->
                Math.max(previous + 1L, System.currentTimeMillis())
        );
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
