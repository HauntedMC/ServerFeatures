package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Receives lifecycle events from the blocking playerdata store. Implementations must be thread-safe:
 * identity resolution and file conversion run away from Paper's main thread.
 */
public interface PlayerDataMigrationObserver {

    PlayerDataMigrationObserver NONE = new PlayerDataMigrationObserver() {
    };

    default void identityResolved(String requestedName, Optional<UUID> playerId) {
    }

    default void operationStarted(UUID playerId) {
    }

    default void operationFinished(UUID playerId) {
    }

    default void migrationDetected(
            UUID playerId,
            int sourceVersion,
            int targetVersion,
            Path backupFile
    ) {
    }

    default void backupCreated(
            UUID playerId,
            int sourceVersion,
            int targetVersion,
            Path backupFile
    ) {
    }

    default void conversionStarted(UUID playerId, int sourceVersion, int targetVersion) {
    }

    default void rollbackStarted(
            UUID playerId,
            int sourceVersion,
            int targetVersion,
            Path backupFile
    ) {
    }

    default void migrationCompleted(UUID playerId, int sourceVersion, int targetVersion) {
    }

    default void backupCleanupFailed(
            UUID playerId,
            int sourceVersion,
            int targetVersion,
            Path backupFile,
            Throwable failure
    ) {
    }

    default void migrationFailed(
            UUID playerId,
            int sourceVersion,
            int targetVersion,
            PlayerDataMigrationException.RecoveryStatus recoveryStatus,
            Path backupFile,
            Throwable failure
    ) {
    }

    default void migrationNotRequired(UUID playerId) {
    }

    default void malformedItemComponentsRemoved(
            UUID playerId,
            String location,
            List<String> componentKeys
    ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(componentKeys, "componentKeys");
    }

    default void loadFailed(UUID playerId, Throwable failure) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(failure, "failure");
    }
}
