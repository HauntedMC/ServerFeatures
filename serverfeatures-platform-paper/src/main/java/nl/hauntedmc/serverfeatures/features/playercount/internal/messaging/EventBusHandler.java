package nl.hauntedmc.serverfeatures.features.playercount.internal.messaging;

import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.api.Subscription;
import nl.hauntedmc.serverfeatures.features.playercount.PlayerCount;
import nl.hauntedmc.serverfeatures.features.playercount.internal.PlayerCountSnapshotStore;
import nl.hauntedmc.serverfeatures.features.playercount.messaging.PlayerCountSnapshotMessage;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final AtomicBoolean closed = new AtomicBoolean();
    private Subscription subscription;

    public EventBusHandler(
            PlayerCount feature,
            MessagingDataAccess redisBus,
            PlayerCountSnapshotStore store
    ) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.redisBus = Objects.requireNonNull(redisBus, "redisBus");
        this.store = Objects.requireNonNull(store, "store");
    }

    public synchronized void subscribe(String channel) {
        if (closed.get()) {
            throw new IllegalStateException("player-count event bus handler is closed");
        }
        if (subscription != null) {
            throw new IllegalStateException("player-count event bus handler is already subscribed");
        }
        Subscription created = Objects.requireNonNull(
                redisBus.subscribe(
                        channel,
                        PlayerCountSnapshotMessage.TYPE,
                        PlayerCountSnapshotMessage.class,
                        this::handleIncoming
                ),
                "Redis subscribe returned no subscription handle"
        );
        if (closed.get()) {
            created.unsubscribe();
            throw new IllegalStateException("player-count event bus handler closed while subscribing");
        }
        subscription = created;
    }

    public void disable() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        Subscription current;
        synchronized (this) {
            current = subscription;
            subscription = null;
        }
        if (current == null) {
            return;
        }
        try {
            CompletableFuture<Void> shutdown = current.unsubscribe();
            if (shutdown == null) {
                feature.getLogger().warning(
                        "Player-count subscription returned no shutdown future."
                );
                return;
            }
            shutdown.orTimeout(UNSUBSCRIBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .whenComplete((ignored, throwable) -> {
                        if (throwable != null) {
                            feature.getLogger().warning(
                                    "Could not confirm player-count subscription shutdown: "
                                            + rootMessage(throwable)
                            );
                        }
                    });
        } catch (RuntimeException exception) {
            feature.getLogger().warning(
                    "Could not start player-count subscription shutdown: " + rootMessage(exception)
            );
        }
    }

    private void handleIncoming(PlayerCountSnapshotMessage message) {
        if (closed.get()) {
            return;
        }
        long now = System.currentTimeMillis();
        PlayerCountSnapshotStore.ApplyResult result = store.apply(message, now);
        if (closed.get()) {
            store.clear();
            return;
        }
        if (result == PlayerCountSnapshotStore.ApplyResult.INVALID && claimInvalidWarning(now)) {
            feature.getLogger().warning(
                    "Ignored an invalid player-count snapshot; repeated warnings are rate-limited."
            );
        }
    }

    private boolean claimInvalidWarning(long nowEpochMillis) {
        while (true) {
            long previous = lastInvalidWarningAt.get();
            if (previous > 0L
                    && nowEpochMillis >= previous
                    && nowEpochMillis - previous < INVALID_WARNING_INTERVAL_MILLIS) {
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
