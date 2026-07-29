package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import de.tr7zw.changeme.nbtapi.NBTType;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBTCompoundList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NbtOfflinePlayerDataMigrationTest {

    private static final int OLD_VERSION = 4440;
    private static final int CURRENT_VERSION = 4903;

    @TempDir
    Path levelDirectory;

    private final FakePlayerDataNbtIo nbtIo = new FakePlayerDataNbtIo();

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

        FakePlayerDataNbtIo.State committed = nbtIo.readState(playerFile);
        assertTrue(backupExistedDuringConversion.get());
        assertFalse(Files.exists(backupFile));
        assertEquals(CURRENT_VERSION, committed.dataVersion());
        assertEquals("converted", committed.migrationMarker());
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
        assertEquals("external-change", nbtIo.readState(playerFile).fixtureMarker());
        assertTrue(Files.isRegularFile(backupFile));
        assertEquals("original", nbtIo.readState(backupFile).fixtureMarker());
    }

    @Test
    void externalChangeAfterReplacementIsNeverRolledBackOver() throws IOException {
        UUID playerId = UUID.randomUUID();
        Path playerFile = playerFile(playerId);
        Path backupFile = migrationBackup(playerFile);
        writeFixture(playerFile, playerId, OLD_VERSION, "original");
        NbtOfflinePlayerDataStore store = store(
                successfulConverter(),
                new RecordingObserver(),
                (ignored, stage) -> {
                    if (stage == PlayerDataMigrationCheckpoint.Stage.AFTER_REPLACE) {
                        writeFixture(
                                playerFile,
                                playerId,
                                CURRENT_VERSION,
                                "external-after-replace"
                        );
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
                "external-after-replace",
                nbtIo.readState(playerFile).fixtureMarker()
        );
        assertTrue(Files.isRegularFile(backupFile));
        assertEquals("original", nbtIo.readState(backupFile).fixtureMarker());
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
        assertEquals("committed", nbtIo.readState(playerFile).fixtureMarker());
    }

    @Test
    void staleBackupNeverOverwritesAValidNewerTarget() throws IOException {
        UUID playerId = UUID.randomUUID();
        Path playerFile = playerFile(playerId);
        Path backupFile = migrationBackup(playerFile);
        writeFixture(playerFile, playerId, CURRENT_VERSION + 1, "newer-target");
        writeFixture(backupFile, playerId, OLD_VERSION, "old-backup");
        NbtOfflinePlayerDataStore store = store(
                successfulConverter(),
                new RecordingObserver(),
                PlayerDataMigrationCheckpoint.NONE
        );

        PlayerDataMigrationException exception = assertThrows(
                PlayerDataMigrationException.class,
                () -> store.load(playerId)
        );

        assertEquals(
                PlayerDataMigrationException.RecoveryStatus.BACKUP_RETAINED,
                exception.recoveryStatus()
        );
        assertEquals("newer-target", nbtIo.readState(playerFile).fixtureMarker());
        assertTrue(Files.isRegularFile(backupFile));
        assertEquals("old-backup", nbtIo.readState(backupFile).fixtureMarker());
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
        assertEquals(CURRENT_VERSION, nbtIo.readState(playerFile).dataVersion());
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
                nbtIo,
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

    private void writeFixture(
            Path file,
            UUID playerId,
            int dataVersion,
            String marker
    ) throws IOException {
        nbtIo.writeState(file, new FakePlayerDataNbtIo.State(
                dataVersion,
                playerId,
                marker,
                ""
        ));
    }

    private static final class FakePlayerDataNbtIo implements PlayerDataNbtIo {
        private final Map<ReadWriteNBT, State> states = Collections.synchronizedMap(
                new IdentityHashMap<>()
        );

        @Override
        public ReadWriteNBT read(byte[] bytes, Path source) throws IOException {
            State state = decode(bytes);
            ReadWriteNBT root = mock(ReadWriteNBT.class);
            states.put(root, state);

            when(root.hasTag("DataVersion", NBTType.NBTTagInt)).thenReturn(true);
            when(root.getInteger("DataVersion")).thenAnswer(ignored -> state(root).dataVersion());
            doAnswer(invocation -> {
                state(root).dataVersion(invocation.getArgument(1));
                return null;
            }).when(root).setInteger(anyString(), anyInt());

            when(root.hasTag("UUID")).thenReturn(true);
            when(root.hasTag("UUID", NBTType.NBTTagIntArray)).thenReturn(true);
            when(root.getUUID("UUID")).thenAnswer(ignored -> state(root).playerId());

            when(root.getString("fixture_marker"))
                    .thenAnswer(ignored -> state(root).fixtureMarker());
            when(root.getString("migration_marker"))
                    .thenAnswer(ignored -> state(root).migrationMarker());
            doAnswer(invocation -> {
                String key = invocation.getArgument(0);
                String value = invocation.getArgument(1);
                if ("fixture_marker".equals(key)) {
                    state(root).fixtureMarker(value);
                } else if ("migration_marker".equals(key)) {
                    state(root).migrationMarker(value);
                }
                return null;
            }).when(root).setString(anyString(), anyString());

            stubEmptyCompoundList(root, "Inventory");
            stubEmptyCompoundList(root, "EnderItems");
            when(root.hasTag("equipment")).thenReturn(false);
            when(root.getCompound("equipment")).thenReturn(null);
            when(root.hasTag("bukkit", NBTType.NBTTagCompound)).thenReturn(false);
            return root;
        }

        @Override
        public void write(Path destination, ReadWriteNBT root) throws IOException {
            writeState(destination, state(root).snapshot());
        }

        private void stubEmptyCompoundList(ReadWriteNBT root, String key) {
            ReadWriteNBTCompoundList list = mock(ReadWriteNBTCompoundList.class);
            when(root.hasTag(key, NBTType.NBTTagList)).thenReturn(true);
            when(root.getCompoundList(key)).thenReturn(list);
            when(root.getListType(key)).thenReturn(NBTType.NBTTagCompound);
            when(list.isEmpty()).thenReturn(true);
            when(list.iterator()).thenAnswer(ignored -> Collections.emptyIterator());
        }

        private State state(ReadWriteNBT root) {
            State state = states.get(root);
            if (state == null) {
                throw new IllegalStateException("Unknown fake playerdata root");
            }
            return state;
        }

        private State readState(Path file) throws IOException {
            return decode(Files.readAllBytes(file));
        }

        private void writeState(Path file, State state) throws IOException {
            Files.createDirectories(file.getParent());
            try (DataOutputStream output = new DataOutputStream(
                    new GZIPOutputStream(Files.newOutputStream(file))
            )) {
                output.writeInt(state.dataVersion());
                output.writeLong(state.playerId().getMostSignificantBits());
                output.writeLong(state.playerId().getLeastSignificantBits());
                output.writeUTF(state.fixtureMarker());
                output.writeUTF(state.migrationMarker());
            }
        }

        private static State decode(byte[] bytes) throws IOException {
            try (DataInputStream input = new DataInputStream(
                    new GZIPInputStream(new java.io.ByteArrayInputStream(bytes))
            )) {
                return new State(
                        input.readInt(),
                        new UUID(input.readLong(), input.readLong()),
                        input.readUTF(),
                        input.readUTF()
                );
            }
        }

        private static final class State {
            private int dataVersion;
            private final UUID playerId;
            private String fixtureMarker;
            private String migrationMarker;

            private State(
                    int dataVersion,
                    UUID playerId,
                    String fixtureMarker,
                    String migrationMarker
            ) {
                this.dataVersion = dataVersion;
                this.playerId = playerId;
                this.fixtureMarker = fixtureMarker;
                this.migrationMarker = migrationMarker;
            }

            private int dataVersion() {
                return dataVersion;
            }

            private void dataVersion(int value) {
                dataVersion = value;
            }

            private UUID playerId() {
                return playerId;
            }

            private String fixtureMarker() {
                return fixtureMarker;
            }

            private void fixtureMarker(String value) {
                fixtureMarker = value;
            }

            private String migrationMarker() {
                return migrationMarker;
            }

            private void migrationMarker(String value) {
                migrationMarker = value;
            }

            private State snapshot() {
                return new State(dataVersion, playerId, fixtureMarker, migrationMarker);
            }
        }
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
