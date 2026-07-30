package nl.hauntedmc.serverfeatures.features.playercount.internal.messaging;

import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.api.Subscription;
import nl.hauntedmc.serverfeatures.features.playercount.PlayerCount;
import nl.hauntedmc.serverfeatures.features.playercount.internal.PlayerCountSnapshotStore;
import nl.hauntedmc.serverfeatures.features.playercount.messaging.PlayerCountSnapshotMessage;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Owns the logical Redis subscription for player-count snapshots.
 */
public final class EventBusHandler {

    private static final long UNSUBSCRIBE_TIMEOUT_SECONDS = 5L;

    private final PlayerCount feature;
    private final MessagingDataAccess redisBus;
    private final PlayerCountSnapshotStore store;
    private Subscription subscription;

    public EventBusHandler(
            PlayerCount feature,
            MessagingDataAccess redisBus,
            PlayerCountSnapshotStore store
    ) {
        this.feature = java.util.Objects.requireNonNull(feature, "feature");
        this.redisBus = java.util.Objects.requireNonNull(redisBus, "redisBus");
        this.store = java.util.Objects.requireNonNull(store, "store");
    }

    public void subscribe(String channel) {
        subscription = redisBus.subscribe(
                channel,
                PlayerCountSnapshotMessage.TYPE,
                PlayerCountSnapshotMessage.class,
                this::handleIncoming
        );
    }

    public void disable() {
        Subscription current = subscription;
        subscription = null;
        if (current == null) {
            return;
        }
        try {
            current.unsubscribe().get(UNSUBSCRIBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            feature.getLogger().warning("Interrupted while unsubscribing from player-count snapshots.");
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            feature.getLogger().warning(
                    "Could not confirm player-count subscription shutdown: " + rootMessage(exception)
            );
        }
    }

    private void handleIncoming(PlayerCountSnapshotMessage message) {
        PlayerCountSnapshotStore.ApplyResult result = store.apply(message, System.currentTimeMillis());
        if (result == PlayerCountSnapshotStore.ApplyResult.INVALID) {
            feature.getLogger().warning("Ignored an invalid player-count snapshot.");
        }
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
