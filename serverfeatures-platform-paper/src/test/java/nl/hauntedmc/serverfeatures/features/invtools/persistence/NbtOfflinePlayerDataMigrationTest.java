package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import de.tr7zw.changeme.nbtapi.NBT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbtOfflinePlayerDataMigrationTest {

    private static final int OLD_VERSION = 4440;
    private static final int CURRENT_VERSION = 4903;

    @TempDir
    Path levelDirectory;

    @Test
    void createsVerifiedBackupBeforeConversionAndDeletesItAfterSuccess() throws IOException {
        UUID playerId = UUID.randomUUID();
        Path playerFile = playerFile(playerId);
        Path backupFile = migrationBackup(playerFile);
        writeFixture(playerFile, playerId, OLD_VERSION, "original");
        RecordingObserver observer = new RecordingObserver();
        AtomicBoolean backupExistedDuringConversion = new AtomicBoolean();
        PlayerDataConverter converter = (source, fromVersion, toVersion) -> {
            backupExistedDuringConversion.set(Files.isRegularFile(backupFile));
            source.setInteger("DataVersion", toVersion);
            source.setString("migration_marker", "converted");
            return source;
        };
        NbtOfflinePlayerDataStore store = store(
                converter,
                observer,
                PlayerDataMigrationCheckpoint.NONE
        );

        OfflinePlayerData loaded = store.load(playerId);

        assertTrue(backupExistedDuringConversion.get());
        assertFalse(Files.exists(backupFile));
        assertEquals(CURRENT_VERSION, NBT.readFile(playerFile.toFile()).getInteger("DataVersion"));
        assertEquals("converted", NBT.readFile(playerFile.toFile()).getString("migration_marker"));
        assertEquals(playerId, loaded.playerId());
        assertEquals(
                List.of("detected", "backup", "converting", "completed"),
                observer.events
        );
    }

    @Test
    void conversionFailureLeavesOriginalBytesUntouchedAndRemovesBackup() throws IOException {
        UUID playerId = UUID.randomUUID();
        Path playerFile = playerFile(playerId);
        Path backupFile = migrationBackup(playerFile);
        writeFixture(playerFile, playerId, OLD_VERSION, "original");
        byte[] originalBytes = Files.readAllBytes(playerFile);
        RecordingObserver observer = new RecordingObserver();
        NbtOfflinePlayerDataStore store = store(
                (source, fromVersion, toVersion) -> {
                    throw new IOException("fixture conversion failure");
                },
                observer,
                PlayerDataMigrationCheckpoint.NONE
        );

        PlayerDataMigrationException exception = assertThrows(
                PlayerDataMigrationException.class,
                () -> store.load(playerId)
        );

        assertEquals(
                PlayerDataMigrationException.RecoveryStatus.ORIGINAL_UNCHANGED,
                exception.recoveryStatus()
        );
        assertArrayEquals(originalBytes, Files.readAllBytes(playerFile));
        assertFalse(Files.exists(backupFile));
        assertTrue(observer.events.contains("failed:ORIGINAL_UNCHANGED"));
    }

    @Test
    void postReplacementFailureRestoresExactOriginalAndRemovesBackup() throws IOException {
        UUID playerId = UUID.randomUUID();
        Path playerFile = playerFile(playerId);
        Path backupFile = migrationBackup(playerFile);
        writeFixture(playerFile, playerId, OLD_VERSION, "original");
        byte[] originalBytes = Files.readAllBytes(playerFile);
        RecordingObserver observer = new RecordingObserver();
        NbtOfflinePlayerDataStore store = store(
                successfulConverter(),
                observer,
                (ignored, stage) -> {
                    if (stage == PlayerDataMigrationCheckpoint.Stage.AFTER_REPLACE) {
                        throw new IOException("fixture post-replace failure");
                    }
                }
        );

        PlayerDataMigrationException exception = assertThrows(
                PlayerDataMigrationException.class,
                () -> store.load(playerId)
        );

        assertEquals(
                PlayerDataMigrationException.RecoveryStatus.RESTORED_FROM_BACKUP,
                exception.recoveryStatus()
        );
        assertArrayEquals(originalBytes, Files.readAllBytes(playerFile));
        assertFalse(Files.exists(backupFile));
        assertTrue(observer.events.contains("restoring"));
        assertTrue(observer.events.contains("failed:RESTORED_FROM_BACKUP"));
    }

    @Test
    void externalChangeBeforeCommitIsNeverOverwrittenAndRetainsBackup() throws IOException {
        UUID playerId = UUID.randomUUID();
        Path playerFile = playerFile(playerId);
        Path backupFile = migrationBackup(playerFile);
        writeFixture(playerFile, playerId, OLD_VERSION, "original");
        RecordingObserver observer = new RecordingObserver();
        NbtOfflinePlayerDataStore store = store(
                successfulConverter(),
                observer,
                (ignored, stage) -> {
                    if (stage == PlayerDataMigrationCheckpoint.Stage.BEFORE_REPLACE) {
                        writeFixture(playerFile, playerId, OLD_VERSION, "external-change");
                    }
                }
        );

        PlayerDataMigrationException exception = assertThrows(
                PlayerDataMigrationException.class,
                () -> store.load(playerId)
        );

        assertEquals(
                PlayerDataMigrationException.RecoveryStatus.BACKUP_RETAINED,
                exception.recoveryStatus()
        );
        assertEquals(
                "external-change",
                NBT.readFile(playerFile.toFile()).getString("fixture_marker")
        );
        assertTrue(Files.isRegularFile(backupFile));
        assertEquals(
                "original",
                NBT.readFile(backupFile.toFile()).getString("fixture_marker")
        );
    }

    @Test
    void staleBackupAfterCommittedMigrationIsCleanedWithoutReconversion() throws IOException {
        UUID playerId = UUID.randomUUID();
        Path playerFile = playerFile(playerId);
        Path backupFile = migrationBackup(playerFile);
        writeFixture(playerFile, playerId, CURRENT_VERSION, "committed");
        writeFixture(backupFile, playerId, OLD_VERSION, "old-backup");
        AtomicBoolean converterCalled = new AtomicBoolean();
        NbtOfflinePlayerDataStore store = store(
                (source, fromVersion, toVersion) -> {
                    converterCalled.set(true);
                    return source;
                },
                new RecordingObserver(),
                PlayerDataMigrationCheckpoint.NONE
        );

        OfflinePlayerData loaded = store.load(playerId);

        assertEquals(playerId, loaded.playerId());
        assertFalse(converterCalled.get());
        assertFalse(Files.exists(backupFile));
        assertEquals("committed", NBT.readFile(playerFile.toFile()).getString("fixture_marker"));
    }

    @Test
    void missingTargetIsRecoveredFromLeftoverBackupBeforeMigration() throws IOException {
        UUID playerId = UUID.randomUUID();
        Path playerFile = playerFile(playerId);
        Path backupFile = migrationBackup(playerFile);
        writeFixture(backupFile, playerId, OLD_VERSION, "recover-me");
        RecordingObserver observer = new RecordingObserver();
        NbtOfflinePlayerDataStore store = store(
                successfulConverter(),
                observer,
                PlayerDataMigrationCheckpoint.NONE
        );

        store.load(playerId);

        assertTrue(Files.isRegularFile(playerFile));
        assertFalse(Files.exists(backupFile));
        assertEquals(CURRENT_VERSION, NBT.readFile(playerFile.toFile()).getInteger("DataVersion"));
        assertTrue(observer.events.contains("restoring"));
    }

    @Test
    void newerPlayerdataIsRejectedWithoutCreatingMigrationBackup() throws IOException {
        UUID playerId = UUID.randomUUID();
        Path playerFile = playerFile(playerId);
        Path backupFile = migrationBackup(playerFile);
        writeFixture(playerFile, playerId, CURRENT_VERSION + 1, "future");
        AtomicBoolean converterCalled = new AtomicBoolean();
        NbtOfflinePlayerDataStore store = store(
                (source, fromVersion, toVersion) -> {
                    converterCalled.set(true);
                    return source;
                },
                new RecordingObserver(),
                PlayerDataMigrationCheckpoint.NONE
        );

        IOException exception = assertThrows(IOException.class, () -> store.load(playerId));

        assertTrue(exception.getMessage().contains("newer than the running Paper"));
        assertFalse(converterCalled.get());
        assertFalse(Files.exists(backupFile));
    }

    private NbtOfflinePlayerDataStore store(
            PlayerDataConverter converter,
            PlayerDataMigrationObserver observer,
            PlayerDataMigrationCheckpoint checkpoint
    ) {
        return new NbtOfflinePlayerDataStore(
                levelDirectory,
                CURRENT_VERSION,
                converter,
                observer,
                checkpoint
        );
    }

    private static PlayerDataConverter successfulConverter() {
        return (source, fromVersion, toVersion) -> {
            source.setInteger("DataVersion", toVersion);
            source.setString("migration_marker", "converted");
            return source;
        };
    }

    private Path playerFile(UUID playerId) {
        return levelDirectory.resolve("players").resolve("data").resolve(playerId + ".dat");
    }

    private static Path migrationBackup(Path playerFile) {
        return playerFile.resolveSibling(
                playerFile.getFileName() + ".invtools-migration-backup"
        );
    }

    /**
     * Writes a minimal compressed Java NBT compound without touching NBT-API's NMS object factory.
     */
    private static void writeFixture(
            Path file,
            UUID playerId,
            int dataVersion,
            String marker
    ) throws IOException {
        Files.createDirectories(file.getParent());
        try (DataOutputStream output = new DataOutputStream(
                new GZIPOutputStream(Files.newOutputStream(file))
        )) {
            output.writeByte(10);
            output.writeUTF("");
            writeInt(output, "DataVersion", dataVersion);
            writeUuid(output, playerId);
            writeString(output, "fixture_marker", marker);
            writeEmptyCompoundList(output, "Inventory");
            writeEmptyCompoundList(output, "EnderItems");
            output.writeByte(10);
            output.writeUTF("equipment");
            output.writeByte(0);
            output.writeByte(0);
        }
    }

    private static void writeInt(DataOutputStream output, String key, int value)
            throws IOException {
        output.writeByte(3);
        output.writeUTF(key);
        output.writeInt(value);
    }

    private static void writeString(DataOutputStream output, String key, String value)
            throws IOException {
        output.writeByte(8);
        output.writeUTF(key);
        output.writeUTF(value);
    }

    private static void writeUuid(DataOutputStream output, UUID playerId) throws IOException {
        output.writeByte(11);
        output.writeUTF("UUID");
        output.writeInt(4);
        long most = playerId.getMostSignificantBits();
        long least = playerId.getLeastSignificantBits();
        output.writeInt((int) (most >> 32));
        output.writeInt((int) most);
        output.writeInt((int) (least >> 32));
        output.writeInt((int) least);
    }

    private static void writeEmptyCompoundList(DataOutputStream output, String key)
            throws IOException {
        output.writeByte(9);
        output.writeUTF(key);
        output.writeByte(10);
        output.writeInt(0);
    }

    private static final class RecordingObserver implements PlayerDataMigrationObserver {
        private final List<String> events = new ArrayList<>();

        @Override
        public void migrationDetected(
                UUID playerId,
                int sourceVersion,
                int targetVersion,
                Path backupFile
        ) {
            events.add("detected");
        }

        @Override
        public void backupCreated(
                UUID playerId,
                int sourceVersion,
                int targetVersion,
                Path backupFile
        ) {
            events.add("backup");
        }

        @Override
        public void conversionStarted(UUID playerId, int sourceVersion, int targetVersion) {
            events.add("converting");
        }

        @Override
        public void rollbackStarted(
                UUID playerId,
                int sourceVersion,
                int targetVersion,
                Path backupFile
        ) {
            events.add("restoring");
        }

        @Override
        public void migrationCompleted(UUID playerId, int sourceVersion, int targetVersion) {
            events.add("completed");
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
            events.add("failed:" + recoveryStatus);
        }
    }
}
