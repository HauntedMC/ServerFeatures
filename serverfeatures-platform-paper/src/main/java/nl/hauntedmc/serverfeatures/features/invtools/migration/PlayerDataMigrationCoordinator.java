package nl.hauntedmc.serverfeatures.features.invtools.migration;

import nl.hauntedmc.serverfeatures.features.invtools.InvTools;
import nl.hauntedmc.serverfeatures.features.invtools.persistence.PlayerDataMigrationException;
import nl.hauntedmc.serverfeatures.features.invtools.persistence.PlayerDataMigrationObserver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Connects asynchronous playerdata work to the staff command that requested it and fences login
 * while the playerdata store is actively reading, migrating, recovering, or saving that UUID.
 */
public final class PlayerDataMigrationCoordinator implements PlayerDataMigrationObserver {

    private static final Duration REQUEST_TTL = Duration.ofSeconds(30);
    private static final Duration ACTIVE_NOTIFICATION_TTL = Duration.ofMinutes(5);
    private static final Runnable NO_SHUTDOWN_BARRIER = () -> {
    };

    private final InvTools feature;
    private final Clock clock;
    private final Map<String, ConcurrentLinkedDeque<Request>> requestsByName =
            new ConcurrentHashMap<>();
    private final Map<UUID, ConcurrentLinkedDeque<Request>> requestsByTarget =
            new ConcurrentHashMap<>();
    private final Map<UUID, AtomicInteger> activeOperations = new ConcurrentHashMap<>();
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final AtomicBoolean closing = new AtomicBoolean();

    private Runnable shutdownBarrier = NO_SHUTDOWN_BARRIER;

    public PlayerDataMigrationCoordinator(InvTools feature) {
        this(feature, Clock.systemUTC());
    }

    PlayerDataMigrationCoordinator(InvTools feature, Clock clock) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Installs the storage close barrier exactly once during feature initialization.
     */
    public synchronized void attachShutdownBarrier(Runnable barrier) {
        Objects.requireNonNull(barrier, "barrier");
        if (closing.get() || !active.get()) {
            throw new IllegalStateException("InvTools migration coordinator is shutting down");
        }
        if (shutdownBarrier != NO_SHUTDOWN_BARRIER) {
            throw new IllegalStateException("InvTools migration shutdown barrier is already attached");
        }
        shutdownBarrier = barrier;
    }

    /**
     * Called on the main thread immediately before InvTools starts an offline command flow.
     */
    public void registerRequest(Player actor, String requestedName) {
        if (!active.get() || closing.get() || actor == null || requestedName == null) {
            return;
        }
        String normalized = normalize(requestedName);
        if (normalized.isEmpty()) {
            return;
        }
        cleanupExpired();
        Request request = new Request(
                actor.getUniqueId(),
                requestedName.trim(),
                now() + REQUEST_TTL.toMillis()
        );
        requestsByName.computeIfAbsent(normalized, ignored -> new ConcurrentLinkedDeque<>())
                .addLast(request);
    }

    /**
     * Returns true only while playerdata I/O is active. The ordinary InvTools login fence protects
     * the preceding identity-resolution and reservation handoff without leaving stale migration
     * fences behind when an offline request is rejected.
     */
    public boolean blocksLogin(UUID playerId) {
        if (!active.get() || playerId == null) {
            return false;
        }
        AtomicInteger activeCount = activeOperations.get(playerId);
        return activeCount != null && activeCount.get() > 0;
    }

    @Override
    public void identityResolved(String requestedName, Optional<UUID> playerId) {
        if (!active.get() || requestedName == null) {
            return;
        }
        cleanupExpired();
        ConcurrentLinkedDeque<Request> queue = requestsByName.remove(normalize(requestedName));
        if (queue == null) {
            return;
        }

        long currentTime = now();
        List<Request> requests = new ArrayList<>();
        Request request;
        while ((request = queue.pollFirst()) != null) {
            if (request.expiresAtMillis() >= currentTime) {
                requests.add(request);
            }
        }
        if (requests.isEmpty() || playerId == null || playerId.isEmpty()) {
            return;
        }

        UUID targetId = playerId.get();
        requestsByTarget.compute(targetId, (ignored, existing) -> {
            ConcurrentLinkedDeque<Request> result = existing == null
                    ? new ConcurrentLinkedDeque<>()
                    : existing;
            result.addAll(requests);
            return result;
        });
    }

    @Override
    public void operationStarted(UUID playerId) {
        if (!active.get() || playerId == null) {
            return;
        }
        long notificationDeadline = now() + ACTIVE_NOTIFICATION_TTL.toMillis();
        requestsByTarget.computeIfPresent(playerId, (ignored, requests) -> {
            ConcurrentLinkedDeque<Request> extended = new ConcurrentLinkedDeque<>();
            for (Request request : requests) {
                extended.addLast(request.withDeadline(notificationDeadline));
            }
            return extended;
        });
        activeOperations.compute(playerId, (ignored, count) -> {
            AtomicInteger result = count == null ? new AtomicInteger() : count;
            result.incrementAndGet();
            return result;
        });
    }

    @Override
    public void operationFinished(UUID playerId) {
        if (playerId == null) {
            return;
        }
        activeOperations.computeIfPresent(playerId, (ignored, count) ->
                count.decrementAndGet() <= 0 ? null : count
        );
    }

    @Override
    public void migrationDetected(
            UUID playerId,
            int sourceVersion,
            int targetVersion,
            Path backupFile
    ) {
        feature.getLogger().info(
                "Playerdata migration detected for " + playerId + ": " + sourceVersion + " -> "
                        + targetVersion + "; recovery backup=" + backupFile
        );
        notifyTarget(playerId, "invtools.migration_detected", sourceVersion, targetVersion);
    }

    @Override
    public void backupCreated(
            UUID playerId,
            int sourceVersion,
            int targetVersion,
            Path backupFile
    ) {
        feature.getLogger().info(
                "Created and verified InvTools migration backup for " + playerId + " at "
                        + backupFile
        );
        notifyTarget(playerId, "invtools.migration_backup_ready", sourceVersion, targetVersion);
    }

    @Override
    public void conversionStarted(UUID playerId, int sourceVersion, int targetVersion) {
        feature.getLogger().info(
                "Running Paper PLAYER data fixer for " + playerId + ": " + sourceVersion + " -> "
                        + targetVersion
        );
        notifyTarget(playerId, "invtools.migration_converting", sourceVersion, targetVersion);
    }

    @Override
    public void rollbackStarted(
            UUID playerId,
            int sourceVersion,
            int targetVersion,
            Path backupFile
    ) {
        feature.getLogger().warning(
                "Restoring playerdata migration backup for " + playerId + " from " + backupFile
        );
        notifyTarget(playerId, "invtools.migration_restoring", sourceVersion, targetVersion);
    }

    @Override
    public void migrationCompleted(UUID playerId, int sourceVersion, int targetVersion) {
        feature.getLogger().info(
                "Safely migrated playerdata for " + playerId + " from " + sourceVersion + " to "
                        + targetVersion
        );
        notifyTarget(playerId, "invtools.migration_completed", sourceVersion, targetVersion);
        clearRequests(playerId);
    }

    @Override
    public void backupCleanupFailed(
            UUID playerId,
            int sourceVersion,
            int targetVersion,
            Path backupFile,
            Throwable failure
    ) {
        logFailure(
                "Migrated playerdata for " + playerId
                        + " but could not delete temporary backup " + backupFile,
                failure
        );
        notifyTarget(
                playerId,
                "invtools.migration_backup_retained",
                sourceVersion,
                targetVersion
        );
        clearRequests(playerId);
    }

    @Override
    public void migrationFailed(
            UUID playerId,
            int sourceVersion,
            int targetVersion,
            PlayerDataMigrationException.RecoveryStatus recoveryStatus,
            Path backupFile,
            Throwable failure
    ) {
        logFailure(
                "Playerdata migration failed for " + playerId + " (" + sourceVersion + " -> "
                        + targetVersion + ", recovery=" + recoveryStatus + ", backup=" + backupFile
                        + ")",
                failure
        );
        String key = switch (recoveryStatus) {
            case ORIGINAL_UNCHANGED -> "invtools.migration_failed_unchanged";
            case RESTORED_FROM_BACKUP -> "invtools.migration_failed_restored";
            case BACKUP_RETAINED -> "invtools.migration_failed_backup_retained";
        };
        notifyTarget(playerId, key, sourceVersion, targetVersion);
        clearRequests(playerId);
    }

    @Override
    public void migrationNotRequired(UUID playerId) {
        clearRequests(playerId);
    }

    @Override
    public void malformedItemComponentsRemoved(
            UUID playerId,
            String location,
            List<String> componentKeys
    ) {
        feature.getLogger().warning(
                "Removed malformed item component(s) " + componentKeys + " from " + location
                        + " in offline playerdata for " + playerId
        );
    }

    @Override
    public void loadFailed(UUID playerId, Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        clearRequests(playerId);
    }

    public void shutdown() {
        if (!closing.compareAndSet(false, true)) {
            return;
        }
        Runnable barrier;
        synchronized (this) {
            barrier = shutdownBarrier;
        }
        try {
            barrier.run();
        } finally {
            active.set(false);
            requestsByName.clear();
            requestsByTarget.clear();
            activeOperations.clear();
        }
    }

    private void notifyTarget(UUID targetId, String key, int sourceVersion, int targetVersion) {
        List<Request> requests = currentRequests(targetId);
        if (requests.isEmpty() || !active.get()) {
            return;
        }
        try {
            feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> {
                for (Request request : requests) {
                    Player actor = Bukkit.getPlayer(request.actorId());
                    if (actor == null || !actor.isOnline()) {
                        continue;
                    }
                    try {
                        actor.sendMessage(feature.getLocalizationHandler()
                                .getMessage(key)
                                .forAudience(actor)
                                .with("player", request.requestedName())
                                .with("from", Integer.toString(sourceVersion))
                                .with("to", Integer.toString(targetVersion))
                                .build());
                    } catch (RuntimeException exception) {
                        feature.getLogger().log(
                                Level.WARNING,
                                "Could not send InvTools migration notification to "
                                        + request.actorId(),
                                exception
                        );
                    }
                }
            });
        } catch (RuntimeException exception) {
            feature.getLogger().log(
                    Level.WARNING,
                    "Could not schedule InvTools migration notification",
                    exception
            );
        }
    }

    private List<Request> currentRequests(UUID playerId) {
        ConcurrentLinkedDeque<Request> requests = requestsByTarget.get(playerId);
        if (requests == null) {
            return List.of();
        }
        long currentTime = now();
        Map<UUID, Request> byActor = new LinkedHashMap<>();
        for (Request request : requests) {
            if (request.expiresAtMillis() >= currentTime) {
                byActor.put(request.actorId(), request);
            }
        }
        return List.copyOf(byActor.values());
    }

    private void clearRequests(UUID playerId) {
        if (playerId != null) {
            requestsByTarget.remove(playerId);
        }
    }

    private void cleanupExpired() {
        long currentTime = now();
        requestsByName.forEach((name, requests) -> {
            requests.removeIf(request -> request.expiresAtMillis() < currentTime);
            if (requests.isEmpty()) {
                requestsByName.remove(name, requests);
            }
        });
        requestsByTarget.forEach((playerId, requests) -> {
            requests.removeIf(request -> request.expiresAtMillis() < currentTime);
            if (requests.isEmpty()) {
                requestsByTarget.remove(playerId, requests);
            }
        });
    }

    private void logFailure(String message, Throwable failure) {
        if (failure == null) {
            feature.getLogger().warning(message + ": unknown failure");
            return;
        }
        feature.getLogger().log(Level.WARNING, message, failure);
    }

    private long now() {
        return clock.millis();
    }

    private static String normalize(String playerName) {
        return playerName.trim().toLowerCase(Locale.ROOT);
    }

    private record Request(UUID actorId, String requestedName, long expiresAtMillis) {
        private Request {
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(requestedName, "requestedName");
        }

        private Request withDeadline(long deadline) {
            return new Request(actorId, requestedName, deadline);
        }
    }
}
