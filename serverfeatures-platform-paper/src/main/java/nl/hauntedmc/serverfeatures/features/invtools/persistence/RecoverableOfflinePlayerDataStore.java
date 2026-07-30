package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import nl.hauntedmc.serverfeatures.features.invtools.model.InventoryKind;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventorySnapshot;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Makes interrupted migration and ordinary edit recovery reachable through the service preflight.
 * Temporary ordinary edit backups are removed only after the committed snapshot can be decoded and
 * is semantically equal to the requested change.
 */
public final class RecoverableOfflinePlayerDataStore implements OfflinePlayerDataStore {

    private static final Logger LOGGER = Logger.getLogger(
            RecoverableOfflinePlayerDataStore.class.getName()
    );
    private static final String MIGRATION_BACKUP_SUFFIX = ".invtools-migration-backup";
    private static final String RECOVERY_BACKUP_SUFFIX = ".invtools-backup";

    private final OfflinePlayerDataStore delegate;
    private final Path playerDataDirectory;

    public RecoverableOfflinePlayerDataStore(
            OfflinePlayerDataStore delegate,
            Path levelDirectory
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        Path normalizedLevelDirectory = Objects.requireNonNull(levelDirectory, "levelDirectory")
                .toAbsolutePath()
                .normalize();
        this.playerDataDirectory = PaperPlayerDataLayout.playerDataDirectory(
                normalizedLevelDirectory
        );
    }

    @Override
    public boolean hasPlayerData(UUID playerId) throws IOException {
        Objects.requireNonNull(playerId, "playerId");
        if (delegate.hasPlayerData(playerId)) {
            return true;
        }
        return isSafeRegularFile(migrationBackup(playerId))
                || isSafeRegularFile(recoveryBackup(playerId));
    }

    @Override
    public OfflinePlayerData load(UUID playerId) throws IOException {
        Objects.requireNonNull(playerId, "playerId");
        restoreRecoveryBackupWhenPrimaryIsMissing(playerId);
        OfflinePlayerData loaded = delegate.load(playerId);
        deleteVerifiedStaleRecoveryBackup(playerId);
        return loaded;
    }

    @Override
    public Optional<UUID> resolvePlayerId(
            Optional<UUID> preferredPlayerId,
            String playerName
    ) throws IOException {
        return delegate.resolvePlayerId(preferredPlayerId, playerName);
    }

    @Override
    public void rememberPlayerIdentity(UUID playerId, String playerName) {
        delegate.rememberPlayerIdentity(playerId, playerName);
    }

    @Override
    public void save(
            OfflinePlayerData original,
            InventoryKind kind,
            InventorySnapshot changedSnapshot
    ) throws IOException {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(changedSnapshot, "changedSnapshot");

        delegate.save(original, kind, changedSnapshot);

        Path backup = recoveryBackup(original.playerId());
        try {
            OfflinePlayerData committed = delegate.load(original.playerId());
            if (changedSnapshot.changedBackingSlots(kind, committed.snapshot()).length != 0) {
                LOGGER.warning(
                        "InvTools committed playerdata for " + original.playerId()
                                + " but the decoded " + kind
                                + " snapshot did not match; retaining recovery backup " + backup
                );
                return;
            }
            deleteRecoveryBackup(backup);
        } catch (IOException | RuntimeException exception) {
            /*
             * The delegate already completed its atomic replacement. Reporting this as a failed
             * save would make the staff-side transfer journal roll back while disk may contain the
             * committed target change. Keep the recovery backup and report the committed-warning
             * state instead; the next successful load retries cleanup.
             */
            LOGGER.log(
                    Level.SEVERE,
                    "InvTools committed playerdata for " + original.playerId()
                            + " but could not verify or clean recovery backup " + backup,
                    exception
            );
        }
    }

    private void restoreRecoveryBackupWhenPrimaryIsMissing(UUID playerId) throws IOException {
        Path target = playerFile(playerId);
        Path backup = recoveryBackup(playerId);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                || !Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        requireSafeRegularFile(backup, "InvTools recovery backup");

        Path temporary = Files.createTempFile(
                playerDataDirectory,
                playerId + ".invtools-recovery-",
                ".dat"
        );
        try {
            Files.copy(backup, temporary, StandardCopyOption.REPLACE_EXISTING);
            forceFile(temporary);
            moveAtomically(temporary, target);
            forceDirectory(playerDataDirectory);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void deleteVerifiedStaleRecoveryBackup(UUID playerId) throws IOException {
        Path backup = recoveryBackup(playerId);
        if (!Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            deleteRecoveryBackup(backup);
        } catch (IOException | RuntimeException exception) {
            LOGGER.log(
                    Level.WARNING,
                    "Could not delete stale InvTools recovery backup " + backup
                            + " after validating current playerdata for " + playerId,
                    exception
            );
        }
    }

    private static void deleteRecoveryBackup(Path backup) throws IOException {
        if (!Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        requireSafeRegularFile(backup, "InvTools recovery backup");
        Files.delete(backup);
        forceDirectory(backup.getParent());
    }

    private static boolean isSafeRegularFile(Path file) {
        return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(file);
    }

    private static void requireSafeRegularFile(Path file, String description) throws IOException {
        if (!isSafeRegularFile(file)) {
            throw new IOException(description + " is not a regular non-symbolic file: " + file);
        }
    }

    private Path playerFile(UUID playerId) throws IOException {
        return resolvePlayerPath(playerId, ".dat");
    }

    private Path migrationBackup(UUID playerId) throws IOException {
        return resolvePlayerPath(playerId, ".dat" + MIGRATION_BACKUP_SUFFIX);
    }

    private Path recoveryBackup(UUID playerId) throws IOException {
        return resolvePlayerPath(playerId, ".dat" + RECOVERY_BACKUP_SUFFIX);
    }

    private Path resolvePlayerPath(UUID playerId, String suffix) throws IOException {
        Path path = playerDataDirectory.resolve(playerId + suffix).normalize();
        if (!path.getParent().equals(playerDataDirectory)) {
            throw new IOException("Resolved playerdata recovery path escaped its directory");
        }
        return path;
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        Files.move(
                source,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
        );
    }

    private static void forceFile(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void forceDirectory(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException ignored) {
            // The file itself was forced and the replacement was atomic.
        }
    }
}
