package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import nl.hauntedmc.serverfeatures.features.invtools.model.InventoryKind;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventorySnapshot;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Makes interrupted migration and ordinary edit recovery reachable through the service preflight.
 * Ordinary edit backups are removed only after the committed snapshot has been decoded and verified.
 * A failed post-commit verification restores and validates the original bytes before failure is
 * reported to the staff-side transfer journal.
 */
public final class RecoverableOfflinePlayerDataStore implements OfflinePlayerDataStore {

    private static final Logger LOGGER = Logger.getLogger(
            RecoverableOfflinePlayerDataStore.class.getName()
    );
    private static final int MAX_RECOVERY_FILE_BYTES = 4 * 1024 * 1024;
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

        UUID playerId = original.playerId();
        Path target = playerFile(playerId);
        Path backup = recoveryBackup(playerId);
        delegate.save(original, kind, changedSnapshot);

        PlayerDataRevision installedRevision;
        try {
            installedRevision = revision(readBoundedRegularFile(target, "committed playerdata"));
        } catch (IOException | RuntimeException exception) {
            throw restoreAfterFailedVerification(
                    original,
                    kind,
                    target,
                    backup,
                    null,
                    new IOException(
                            "Could not read the committed offline playerdata for " + playerId,
                            exception
                    )
            );
        }

        try {
            OfflinePlayerData committed = delegate.load(playerId);
            if (!installedRevision.equals(committed.revision())) {
                throw new IOException(
                        "Committed playerdata changed while InvTools was verifying it for " + playerId
                );
            }
            if (changedSnapshot.changedBackingSlots(kind, committed.snapshot()).length != 0) {
                throw new IOException(
                        "Committed offline playerdata did not match the requested " + kind
                                + " snapshot for " + playerId
                );
            }
            requireRevision(
                    installedRevision,
                    readBoundedRegularFile(target, "committed playerdata"),
                    playerId
            );
        } catch (IOException | RuntimeException verificationFailure) {
            throw restoreAfterFailedVerification(
                    original,
                    kind,
                    target,
                    backup,
                    installedRevision,
                    new IOException(
                            "Could not verify the committed offline playerdata for " + playerId,
                            verificationFailure
                    )
            );
        }

        try {
            deleteRecoveryBackup(backup);
        } catch (IOException | RuntimeException cleanupFailure) {
            /*
             * The target has already been decoded, revision-checked, and semantically verified.
             * Failing this save now would make the staff-side journal roll back a committed change.
             * Keep the redundant recovery file and let the next successful load retry cleanup.
             */
            LOGGER.log(
                    Level.WARNING,
                    "InvTools safely committed playerdata for " + playerId
                            + " but could not delete redundant recovery backup " + backup,
                    cleanupFailure
            );
        }
    }

    private IOException restoreAfterFailedVerification(
            OfflinePlayerData original,
            InventoryKind kind,
            Path target,
            Path backup,
            PlayerDataRevision installedRevision,
            IOException primaryFailure
    ) {
        UUID playerId = original.playerId();
        try {
            if (!canRestoreCommittedTarget(target, installedRevision)) {
                PlayerDataConflictException conflict = new PlayerDataConflictException(
                        "Playerdata changed externally after InvTools committed it for " + playerId
                                + "; the recovery backup was retained at " + backup
                );
                conflict.initCause(primaryFailure);
                return conflict;
            }
            restoreOrdinaryBackup(target, backup, original, kind);
            return new IOException(
                    "Offline playerdata verification failed for " + playerId
                            + "; the exact original file was restored and verified",
                    primaryFailure
            );
        } catch (IOException | RuntimeException recoveryFailure) {
            IOException result = new IOException(
                    "Offline playerdata verification and automatic recovery failed for " + playerId
                            + "; recovery backup retained at " + backup,
                    primaryFailure
            );
            result.addSuppressed(recoveryFailure);
            return result;
        }
    }

    private boolean canRestoreCommittedTarget(
            Path target,
            PlayerDataRevision installedRevision
    ) throws IOException {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return true;
        }
        if (!isSafeRegularFile(target) || installedRevision == null) {
            return false;
        }
        return installedRevision.equals(revision(
                readBoundedRegularFile(target, "committed playerdata")
        ));
    }

    private void restoreOrdinaryBackup(
            Path target,
            Path backup,
            OfflinePlayerData original,
            InventoryKind kind
    ) throws IOException {
        UUID playerId = original.playerId();
        byte[] backupBytes = readBoundedRegularFile(backup, "InvTools recovery backup");
        PlayerDataRevision backupRevision = revision(backupBytes);
        Path temporary = Files.createTempFile(
                playerDataDirectory,
                playerId + ".invtools-restore-",
                ".dat"
        );
        try {
            Files.copy(
                    backup,
                    temporary,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES
            );
            forceFile(temporary);
            requireRevision(
                    backupRevision,
                    readBoundedRegularFile(temporary, "temporary recovery file"),
                    playerId
            );
            moveAtomically(temporary, target);
            forceDirectory(playerDataDirectory);
            requireRevision(
                    backupRevision,
                    readBoundedRegularFile(target, "restored playerdata"),
                    playerId
            );

            OfflinePlayerData restored = delegate.load(playerId);
            if (!backupRevision.equals(restored.revision())
                    || original.snapshot().changedBackingSlots(kind, restored.snapshot()).length != 0) {
                throw new IOException(
                        "Restored playerdata did not match the original " + kind
                                + " snapshot for " + playerId
                );
            }
            requireRevision(
                    backupRevision,
                    readBoundedRegularFile(target, "restored playerdata"),
                    playerId
            );
            deleteRecoveryBackup(backup);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void restoreRecoveryBackupWhenPrimaryIsMissing(UUID playerId) throws IOException {
        Path target = playerFile(playerId);
        Path backup = recoveryBackup(playerId);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                || !Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        byte[] backupBytes = readBoundedRegularFile(backup, "InvTools recovery backup");
        PlayerDataRevision backupRevision = revision(backupBytes);
        Path temporary = Files.createTempFile(
                playerDataDirectory,
                playerId + ".invtools-recovery-",
                ".dat"
        );
        try {
            Files.copy(
                    backup,
                    temporary,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES
            );
            forceFile(temporary);
            requireRevision(
                    backupRevision,
                    readBoundedRegularFile(temporary, "temporary recovery file"),
                    playerId
            );
            moveAtomically(temporary, target);
            forceDirectory(playerDataDirectory);
            requireRevision(
                    backupRevision,
                    readBoundedRegularFile(target, "restored playerdata"),
                    playerId
            );
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

    private static byte[] readBoundedRegularFile(Path file, String description) throws IOException {
        requireSafeRegularFile(file, description);
        try (InputStream input = Files.newInputStream(
                file,
                StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS
        )) {
            byte[] bytes = input.readNBytes(MAX_RECOVERY_FILE_BYTES + 1);
            if (bytes.length > MAX_RECOVERY_FILE_BYTES) {
                throw new IOException(description + " exceeds the safe recovery size: " + file);
            }
            return bytes;
        }
    }

    private static PlayerDataRevision revision(byte[] bytes) throws IOException {
        try {
            return new PlayerDataRevision(HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)
            ));
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is unavailable", exception);
        }
    }

    private static void requireRevision(
            PlayerDataRevision expected,
            byte[] bytes,
            UUID playerId
    ) throws IOException {
        if (!expected.equals(revision(bytes))) {
            throw new IOException("Playerdata revision verification failed for " + playerId);
        }
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
