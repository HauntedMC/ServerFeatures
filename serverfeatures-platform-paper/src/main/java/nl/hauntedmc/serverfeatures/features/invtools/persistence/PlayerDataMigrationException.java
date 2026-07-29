package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

public final class PlayerDataMigrationException extends IOException {

    private final UUID playerId;
    private final int sourceVersion;
    private final int targetVersion;
    private final RecoveryStatus recoveryStatus;
    private final Path backupFile;

    public PlayerDataMigrationException(
            String message,
            UUID playerId,
            int sourceVersion,
            int targetVersion,
            RecoveryStatus recoveryStatus,
            Path backupFile,
            Throwable cause
    ) {
        super(message, cause);
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.sourceVersion = sourceVersion;
        this.targetVersion = targetVersion;
        this.recoveryStatus = Objects.requireNonNull(recoveryStatus, "recoveryStatus");
        this.backupFile = Objects.requireNonNull(backupFile, "backupFile");
    }

    public UUID playerId() {
        return playerId;
    }

    public int sourceVersion() {
        return sourceVersion;
    }

    public int targetVersion() {
        return targetVersion;
    }

    public RecoveryStatus recoveryStatus() {
        return recoveryStatus;
    }

    public Path backupFile() {
        return backupFile;
    }

    public enum RecoveryStatus {
        ORIGINAL_UNCHANGED,
        RESTORED_FROM_BACKUP,
        BACKUP_RETAINED
    }
}
