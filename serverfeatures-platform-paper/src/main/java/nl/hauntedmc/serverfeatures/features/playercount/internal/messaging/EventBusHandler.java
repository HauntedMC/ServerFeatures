package nl.hauntedmc.serverfeatures.features.playercount.internal.messaging;

import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.api.Subscription;
import nl.hauntedmc.serverfeatures.features.playercount.PlayerCount;
import nl.hauntedmc.serverfeatures.features.playercount.internal.PlayerCountSnapshotStore;
import nl.hauntedmc.serverfeatures.features.playercount.messaging.PlayerCountSnapshotMessage;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns the logical Redis subscription for player-count snapshots.
 */
public final class EventBusHandler {

    private static final long UNSUBSCRIBE_TIMEOUT_SECONDS = 5L;
    private static final long INVALID_WARNING_INTERVAL_MILLIS = 60_000L;

    private final PlayerCount feature;
    private final MessagingDataAccess redisBus;
    private final PlayerCountSnapshotStore store;
    private final AtomicLong lastInvalidWarningAt = new AtomicLong();
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
        long now = System.currentTimeMillis();
        PlayerCountSnapshotStore.ApplyResult result = store.apply(message, now);
        if (result == PlayerCountSnapshotStore.ApplyResult.INVALID && claimInvalidWarning(now)) {
            feature.getLogger().warning(
                    "Ignored an invalid player-count snapshot; repeated warnings are rate-limited."
            );
        }
    }

    private boolean claimInvalidWarning(long nowEpochMillis) {
        while (true) {
            long previous = lastInvalidWarningAt.get();
            if (previous > 0L && nowEpochMillis - previous < INVALID_WARNING_INTERVAL_MILLIS) {
                return false;
            }
            if (lastInvalidWarningAt.compareAndSet(previous, nowEpochMillis)) {
                return true;
            }
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
