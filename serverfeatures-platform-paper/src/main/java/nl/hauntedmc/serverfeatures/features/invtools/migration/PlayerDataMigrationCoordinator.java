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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Connects asynchronous playerdata work to the staff command that requested it and exposes an
 * early-login fence independent of Paper's player construction lifecycle.
 */
public final class PlayerDataMigrationCoordinator implements PlayerDataMigrationObserver {

    private static final Duration REQUEST_TTL = Duration.ofSeconds(30);
    private static final Duration PENDING_OPERATION_TTL = Duration.ofSeconds(30);

    private final InvTools feature;
    private final Clock clock;
    private final Map<String, ConcurrentLinkedDeque<Request>> requestsByName =
            new ConcurrentHashMap<>();
    private final Map<UUID, ConcurrentLinkedDeque<Request>> requestsByTarget =
            new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingUntil = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicInteger> activeOperations = new ConcurrentHashMap<>();
    private final AtomicBoolean active = new AtomicBoolean(true);

    public PlayerDataMigrationCoordinator(InvTools feature) {
        this(feature, Clock.systemUTC());
    }

    PlayerDataMigrationCoordinator(InvTools feature, Clock clock) {
        this.feature = java.util.Objects.requireNonNull(feature, "feature");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    /**
     * Called on the main thread immediately before InvTools starts an offline command flow.
     */
    public void registerRequest(Player actor, String requestedName) {
        if (!active.get() || actor == null || requestedName == null) {
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
     * Returns true while identity-to-file handoff or actual playerdata I/O is in progress.
     */
    public boolean blocksLogin(UUID playerId) {
        if (!active.get() || playerId == null) {
            return false;
        }
        cleanupExpired();
        AtomicInteger activeCount = activeOperations.get(playerId);
        if (activeCount != null && activeCount.get() > 0) {
            return true;
        }
        Long deadline = pendingUntil.get(playerId);
        return deadline != null && deadline >= now();
    }

    @Override
    public void identityResolved(String requestedName, Optional<UUID> playerId) {
        if (!active.get() || requestedName == null) {
            return;
        }
        cleanupExpired();
        String normalized = normalize(requestedName);
        ConcurrentLinkedDeque<Request> queue = requestsByName.get(normalized);
        if (queue == null) {
            return;
        }
        Request request;
        do {
            request = queue.pollFirst();
        } while (request != null && request.expiresAtMillis() < now());
        if (queue.isEmpty()) {
            requestsByName.remove(normalized, queue);
        }
        if (request == null || playerId == null || playerId.isEmpty()) {
            return;
        }

        UUID targetId = playerId.get();
        requestsByTarget.computeIfAbsent(targetId, ignored -> new ConcurrentLinkedDeque<>())
                .addLast(request);
        pendingUntil.merge(
                targetId,
                now() + PENDING_OPERATION_TTL.toMillis(),
                Math::max
        );
    }

    @Override
    public void operationStarted(UUID playerId) {
        if (!active.get() || playerId == null) {
            return;
        }
        pendingUntil.remove(playerId);
        activeOperations.computeIfAbsent(playerId, ignored -> new AtomicInteger())
                .incrementAndGet();
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
        notifyTarget(
                playerId,
                "invtools.migration_detected",
                sourceVersion,
                targetVersion
        );
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
        notifyTarget(
                playerId,
                "invtools.migration_backup_ready",
                sourceVersion,
                targetVersion
        );
    }

    @Override
    public void conversionStarted(UUID playerId, int sourceVersion, int targetVersion) {
        feature.getLogger().info(
                "Running Paper PLAYER data fixer for " + playerId + ": " + sourceVersion + " -> "
                        + targetVersion
        );
        notifyTarget(
                playerId,
                "invtools.migration_converting",
                sourceVersion,
                targetVersion
        );
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
        notifyTarget(
                playerId,
                "invtools.migration_restoring",
                sourceVersion,
                targetVersion
        );
    }

    @Override
    public void migrationCompleted(UUID playerId, int sourceVersion, int targetVersion) {
        feature.getLogger().info(
                "Safely migrated playerdata for " + playerId + " from " + sourceVersion + " to "
                        + targetVersion
        );
        notifyTarget(
                playerId,
                "invtools.migration_completed",
                sourceVersion,
                targetVersion
        );
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
        String detail = failure == null ? "unknown failure" : failure.getMessage();
        feature.getLogger().warning(
                "Migrated playerdata for " + playerId + " but could not delete temporary backup "
                        + backupFile + ": " + detail
        );
        notifyTarget(
                playerId,
                "invtools.migration_backup_retained",
                sourceVersion,
                targetVersion
        );
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
        String detail = failure == null ? "unknown failure" : failure.getMessage();
        feature.getLogger().warning(
                "Playerdata migration failed for " + playerId + " (" + sourceVersion + " -> "
                        + targetVersion + ", recovery=" + recoveryStatus + ", backup=" + backupFile
                        + "): " + detail
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
    public void loadFailed(UUID playerId, Throwable failure) {
        clearRequests(playerId);
    }

    public void shutdown() {
        if (!active.compareAndSet(true, false)) {
            return;
        }
        requestsByName.clear();
        requestsByTarget.clear();
        pendingUntil.clear();
        activeOperations.clear();
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
                    actor.sendMessage(feature.getLocalizationHandler()
                            .getMessage(key)
                            .forAudience(actor)
                            .with("player", request.requestedName())
                            .with("from", Integer.toString(sourceVersion))
                            .with("to", Integer.toString(targetVersion))
                            .build());
                }
            });
        } catch (RuntimeException exception) {
            feature.getLogger().warning(
                    "Could not schedule InvTools migration notification: " + exception.getMessage()
            );
        }
    }

    private List<Request> currentRequests(UUID playerId) {
        ConcurrentLinkedDeque<Request> requests = requestsByTarget.get(playerId);
        if (requests == null) {
            return List.of();
        }
        long currentTime = now();
        List<Request> result = new ArrayList<>();
        for (Request request : requests) {
            if (request.expiresAtMillis() >= currentTime) {
                result.add(request);
            }
        }
        return List.copyOf(result);
    }

    private void clearRequests(UUID playerId) {
        if (playerId == null) {
            return;
        }
        requestsByTarget.remove(playerId);
        pendingUntil.remove(playerId);
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
        pendingUntil.entrySet().removeIf(entry -> entry.getValue() < currentTime);
    }

    private long now() {
        return clock.millis();
    }

    private static String normalize(String playerName) {
        return playerName.trim().toLowerCase(Locale.ROOT);
    }

    private record Request(UUID actorId, String requestedName, long expiresAtMillis) {
    }
}
