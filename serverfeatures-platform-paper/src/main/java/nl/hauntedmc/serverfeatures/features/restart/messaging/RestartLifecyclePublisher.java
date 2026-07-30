package nl.hauntedmc.serverfeatures.features.restart.messaging;

import nl.hauntedmc.dataprovider.database.messaging.durable.DurableEvent;
import nl.hauntedmc.dataprovider.database.messaging.durable.DurableMessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.durable.PublishedDurableEvent;
import nl.hauntedmc.serverfeatures.api.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.restart.Restart;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Publishes restart lifecycle state and persists the restart identity across the Paper reboot.
 */
public final class RestartLifecyclePublisher {

    private final Restart feature;
    private final DurableMessagingDataAccess messaging;
    private final RestartMarkerStore markerStore;
    private final String stream;
    private final String serverName;
    private final long reconnectDelayMillis;
    private final long playerIntervalMillis;
    private final long sessionTtlMillis;
    private final int readyPublishAttempts;
    private final int readyRetrySeconds;
    private final AtomicBoolean readyPublishing = new AtomicBoolean(false);
    private volatile boolean closed;

    public RestartLifecyclePublisher(
            Restart feature,
            DurableMessagingDataAccess messaging,
            RestartMarkerStore markerStore,
            String stream,
            String serverName
    ) {
        this.feature = feature;
        this.messaging = messaging;
        this.markerStore = markerStore;
        this.stream = stream;
        this.serverName = normalizeServerName(serverName);
        this.reconnectDelayMillis = secondsToMillis(feature.getPositiveInt(
                "autoreconnect.wait_after_ready_seconds",
                5
        ));
        this.playerIntervalMillis = feature.getPositiveLong(
                "autoreconnect.player_interval_millis",
                250L
        );
        this.sessionTtlMillis = secondsToMillis(feature.getPositiveInt(
                "autoreconnect.session_ttl_seconds",
                600
        ));
        this.readyPublishAttempts = feature.getPositiveInt("autoreconnect.ready_publish_attempts", 12);
        this.readyRetrySeconds = feature.getPositiveInt("autoreconnect.ready_retry_seconds", 5);
    }

    public CompletableFuture<PublishedDurableEvent> publishPrepare(Collection<? extends Player> players) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("Restart lifecycle publisher is closed"));
        }
        long now = System.currentTimeMillis();
        String restartId = UUID.randomUUID().toString();
        RestartMarker marker = new RestartMarker(
                restartId,
                serverName,
                now,
                now + sessionTtlMillis,
                reconnectDelayMillis,
                playerIntervalMillis
        );
        try {
            markerStore.save(marker);
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(exception);
        }

        List<String> playerIds = players == null
                ? List.of()
                : players.stream()
                .map(Player::getUniqueId)
                .sorted(Comparator.comparing(UUID::toString))
                .map(UUID::toString)
                .toList();
        RestartLifecycleMessage message = message(
                marker,
                RestartLifecycleMessage.ACTION_PREPARE,
                "prepare",
                playerIds
        );
        feature.getLogger().info(
                "Publishing restart PREPARE {} for '{}' with {} player(s).",
                restartId,
                serverName,
                playerIds.size()
        );
        return publish(message);
    }

    /** Called only after Paper has fully loaded worlds and plugins. */
    public void publishReadyAfterServerLoad() {
        if (closed || !readyPublishing.compareAndSet(false, true)) {
            return;
        }
        RestartMarker marker;
        try {
            marker = markerStore.load().orElse(null);
        } catch (IOException exception) {
            readyPublishing.set(false);
            feature.getLogger().warning("Could not read restart autoreconnect marker: " + exception.getMessage());
            return;
        }
        if (marker == null) {
            readyPublishing.set(false);
            return;
        }
        if (marker.expiresAtEpochMillis() <= System.currentTimeMillis()) {
            readyPublishing.set(false);
            deleteMarker("expired restart marker");
            return;
        }
        publishReadyAttempt(marker, 1);
    }

    public void close() {
        closed = true;
    }

    private void publishReadyAttempt(RestartMarker marker, int attempt) {
        if (closed) {
            readyPublishing.set(false);
            return;
        }
        RestartLifecycleMessage message = message(
                marker,
                RestartLifecycleMessage.ACTION_READY,
                "ready",
                List.of()
        );
        publish(message).whenComplete((published, throwable) -> {
            if (throwable == null) {
                feature.getLogger().info(
                        "Published restart READY {} for '{}' after full server load.",
                        marker.restartId(),
                        marker.serverName()
                );
                deleteMarker("published READY");
                readyPublishing.set(false);
                return;
            }
            if (closed || marker.expiresAtEpochMillis() <= System.currentTimeMillis()) {
                readyPublishing.set(false);
                deleteMarker("expired after READY publication failure");
                return;
            }
            if (attempt >= readyPublishAttempts) {
                readyPublishing.set(false);
                feature.getLogger().severe(
                        "Could not publish restart READY after " + attempt + " attempts: " + rootMessage(throwable)
                );
                return;
            }
            feature.getLogger().warning(
                    "Restart READY publication attempt " + attempt + " failed; retrying: " + rootMessage(throwable)
            );
            feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(
                    () -> publishReadyAttempt(marker, attempt + 1),
                    BukkitTime.ticks(readyRetrySeconds * 20L)
            );
        });
    }

    private CompletableFuture<PublishedDurableEvent> publish(RestartLifecycleMessage message) {
        DurableEvent<RestartLifecycleMessage> event = new DurableEvent<>(
                message.getOperationId(),
                message.getOperationId(),
                message
        );
        try {
            return messaging.publish(stream, event);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private RestartLifecycleMessage message(
            RestartMarker marker,
            String action,
            String operationSuffix,
            List<String> playerIds
    ) {
        return new RestartLifecycleMessage(
                "restart." + marker.restartId() + "." + operationSuffix,
                marker.restartId(),
                action,
                marker.serverName(),
                marker.createdAtEpochMillis(),
                marker.expiresAtEpochMillis(),
                marker.reconnectDelayMillis(),
                marker.playerIntervalMillis(),
                playerIds
        );
    }

    private void deleteMarker(String reason) {
        try {
            markerStore.delete();
        } catch (IOException exception) {
            feature.getLogger().warning(
                    "Could not delete restart autoreconnect marker after " + reason + ": " + exception.getMessage()
            );
        }
    }

    private static String normalizeServerName(String value) {
        String normalized = value == null ? "server" : value.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9_.:-]", "_").replaceAll("_+", "_");
        return normalized.isBlank() ? "server" : normalized.substring(0, Math.min(normalized.length(), 150));
    }

    private static long secondsToMillis(int seconds) {
        return Math.multiplyExact((long) seconds, 1_000L);
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
