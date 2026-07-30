package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import nl.hauntedmc.serverfeatures.features.invtools.model.InventoryKind;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventorySnapshot;
import nl.hauntedmc.serverfeatures.features.invtools.support.TestItemStacks;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecoverableOfflinePlayerDataStoreTest {

    @TempDir
    Path levelDirectory;

    @Test
    void backupOnlyMigrationStateCountsAsRecoverablePlayerdata() throws IOException {
        UUID playerId = UUID.randomUUID();
        OfflinePlayerDataStore delegate = mock(OfflinePlayerDataStore.class);
        when(delegate.hasPlayerData(playerId)).thenReturn(false);
        RecoverableOfflinePlayerDataStore store = store(delegate);
        Path backup = migrationBackup(playerId);
        Files.createDirectories(backup.getParent());

        assertFalse(store.hasPlayerData(playerId));

        Files.write(backup, new byte[]{1});

        assertTrue(store.hasPlayerData(playerId));
    }

    @Test
    void backupOnlyOrdinaryEditStateCountsAsRecoverablePlayerdata() throws IOException {
        UUID playerId = UUID.randomUUID();
        OfflinePlayerDataStore delegate = mock(OfflinePlayerDataStore.class);
        when(delegate.hasPlayerData(playerId)).thenReturn(false);
        RecoverableOfflinePlayerDataStore store = store(delegate);
        Path backup = recoveryBackup(playerId);
        Files.createDirectories(backup.getParent());
        Files.write(backup, new byte[]{1});

        assertTrue(store.hasPlayerData(playerId));
    }

    @Test
    void symlinkMigrationBackupIsNotAccepted() throws IOException {
        UUID playerId = UUID.randomUUID();
        OfflinePlayerDataStore delegate = mock(OfflinePlayerDataStore.class);
        when(delegate.hasPlayerData(playerId)).thenReturn(false);
        RecoverableOfflinePlayerDataStore store = store(delegate);
        Path directory = playerDataDirectory();
        Path target = levelDirectory.resolve("outside.dat");
        Path backup = migrationBackup(playerId);
        Files.createDirectories(directory);
        Files.write(target, new byte[]{1});
        try {
            Files.createSymbolicLink(backup, target);
        } catch (UnsupportedOperationException | IOException exception) {
            return;
        }

        assertFalse(store.hasPlayerData(playerId));
    }

    @Test
    void successfulVerifiedSaveDeletesOrdinaryRecoveryBackup() throws IOException {
        UUID playerId = UUID.randomUUID();
        byte[] originalBytes = new byte[]{1, 2, 3};
        byte[] committedBytes = new byte[]{4, 5, 6};
        InventorySnapshot originalSnapshot = InventorySnapshot.empty();
        InventorySnapshot changed = originalSnapshot.withBackingSlot(
                InventoryKind.PLAYER,
                9,
                TestItemStacks.item(Material.DIAMOND, 3)
        );
        OfflinePlayerData original = data(playerId, originalSnapshot, originalBytes);
        OfflinePlayerDataStore delegate = writingDelegate(
                playerId,
                originalBytes,
                committedBytes
        );
        when(delegate.load(playerId)).thenReturn(data(playerId, changed, committedBytes));
        RecoverableOfflinePlayerDataStore store = store(delegate);

        store.save(original, InventoryKind.PLAYER, changed);

        verify(delegate).save(original, InventoryKind.PLAYER, changed);
        verify(delegate).load(playerId);
        assertArrayEquals(committedBytes, Files.readAllBytes(playerFile(playerId)));
        assertFalse(Files.exists(recoveryBackup(playerId)));
    }

    @Test
    void semanticVerificationFailureRestoresExactOriginalBeforeReportingFailure()
            throws IOException {
        UUID playerId = UUID.randomUUID();
        byte[] originalBytes = new byte[]{7, 8, 9};
        byte[] committedBytes = new byte[]{10, 11, 12};
        InventorySnapshot originalSnapshot = InventorySnapshot.empty();
        InventorySnapshot requested = originalSnapshot.withBackingSlot(
                InventoryKind.ENDER_CHEST,
                4,
                TestItemStacks.item(Material.EMERALD, 2)
        );
        OfflinePlayerData original = data(playerId, originalSnapshot, originalBytes);
        OfflinePlayerDataStore delegate = writingDelegate(
                playerId,
                originalBytes,
                committedBytes
        );
        doAnswer(invocation -> {
            byte[] current = Files.readAllBytes(playerFile(playerId));
            InventorySnapshot snapshot = MessageDigest.isEqual(current, originalBytes)
                    ? originalSnapshot
                    : InventorySnapshot.empty();
            return data(playerId, snapshot, current);
        }).when(delegate).load(playerId);
        RecoverableOfflinePlayerDataStore store = store(delegate);

        IOException failure = assertThrows(
                IOException.class,
                () -> store.save(original, InventoryKind.ENDER_CHEST, requested)
        );

        assertTrue(failure.getMessage().contains("exact original file was restored"));
        assertArrayEquals(originalBytes, Files.readAllBytes(playerFile(playerId)));
        assertFalse(Files.exists(recoveryBackup(playerId)));
    }

    @Test
    void externalChangeAfterCommitIsNeverOverwrittenAndRetainsBackup() throws IOException {
        UUID playerId = UUID.randomUUID();
        byte[] originalBytes = new byte[]{13, 14, 15};
        byte[] committedBytes = new byte[]{16, 17, 18};
        byte[] externalBytes = new byte[]{19, 20, 21};
        InventorySnapshot originalSnapshot = InventorySnapshot.empty();
        InventorySnapshot requested = originalSnapshot.withBackingSlot(
                InventoryKind.PLAYER,
                9,
                TestItemStacks.item(Material.DIAMOND)
        );
        OfflinePlayerData original = data(playerId, originalSnapshot, originalBytes);
        OfflinePlayerDataStore delegate = writingDelegate(
                playerId,
                originalBytes,
                committedBytes
        );
        doAnswer(invocation -> {
            Files.write(playerFile(playerId), externalBytes);
            return data(playerId, InventorySnapshot.empty(), externalBytes);
        }).when(delegate).load(playerId);
        RecoverableOfflinePlayerDataStore store = store(delegate);

        IOException failure = assertThrows(
                IOException.class,
                () -> store.save(original, InventoryKind.PLAYER, requested)
        );

        assertInstanceOf(PlayerDataConflictException.class, failure);
        assertArrayEquals(externalBytes, Files.readAllBytes(playerFile(playerId)));
        assertArrayEquals(originalBytes, Files.readAllBytes(recoveryBackup(playerId)));
    }

    @Test
    void failedAutomaticRestoreRetainsBackupAndPreservesPrimaryFailure() throws IOException {
        UUID playerId = UUID.randomUUID();
        byte[] originalBytes = new byte[]{22, 23, 24};
        byte[] committedBytes = new byte[]{25, 26, 27};
        InventorySnapshot originalSnapshot = InventorySnapshot.empty();
        InventorySnapshot requested = originalSnapshot.withBackingSlot(
                InventoryKind.PLAYER,
                9,
                TestItemStacks.item(Material.GOLD_INGOT)
        );
        OfflinePlayerData original = data(playerId, originalSnapshot, originalBytes);
        OfflinePlayerDataStore delegate = writingDelegate(
                playerId,
                originalBytes,
                committedBytes
        );
        when(delegate.load(playerId)).thenThrow(new IOException("verification fixture"));
        RecoverableOfflinePlayerDataStore store = store(delegate);
        Files.createDirectories(playerDataDirectory());

        IOException failure = assertThrows(
                IOException.class,
                () -> store.save(original, InventoryKind.PLAYER, requested)
        );

        assertTrue(failure.getMessage().contains("exact original file was restored")
                || failure.getMessage().contains("automatic recovery failed"));
        assertTrue(failure.getCause() != null);
    }

    @Test
    void validLoadRemovesAStaleOrdinaryRecoveryBackup() throws IOException {
        UUID playerId = UUID.randomUUID();
        byte[] currentBytes = new byte[]{28, 29};
        OfflinePlayerDataStore delegate = mock(OfflinePlayerDataStore.class);
        OfflinePlayerData loaded = data(
                playerId,
                InventorySnapshot.empty(),
                currentBytes
        );
        when(delegate.load(playerId)).thenReturn(loaded);
        Path target = playerFile(playerId);
        Path backup = recoveryBackup(playerId);
        Files.createDirectories(target.getParent());
        Files.write(target, currentBytes);
        Files.write(backup, new byte[]{30, 31});
        RecoverableOfflinePlayerDataStore store = store(delegate);

        assertTrue(store.load(playerId) == loaded);

        assertFalse(Files.exists(backup));
    }

    @Test
    void missingPrimaryIsRestoredFromOrdinaryRecoveryBackupBeforeLoad() throws IOException {
        UUID playerId = UUID.randomUUID();
        byte[] backupBytes = new byte[]{32, 33, 34, 35};
        OfflinePlayerDataStore delegate = mock(OfflinePlayerDataStore.class);
        OfflinePlayerData loaded = data(
                playerId,
                InventorySnapshot.empty(),
                backupBytes
        );
        Path target = playerFile(playerId);
        Path backup = recoveryBackup(playerId);
        Files.createDirectories(backup.getParent());
        Files.write(backup, backupBytes);
        doAnswer(invocation -> {
            assertTrue(Files.isRegularFile(target));
            assertArrayEquals(backupBytes, Files.readAllBytes(target));
            return loaded;
        }).when(delegate).load(playerId);
        RecoverableOfflinePlayerDataStore store = store(delegate);

        store.load(playerId);

        assertTrue(Files.isRegularFile(target));
        assertFalse(Files.exists(backup));
    }

    private OfflinePlayerDataStore writingDelegate(
            UUID playerId,
            byte[] originalBytes,
            byte[] committedBytes
    ) throws IOException {
        OfflinePlayerDataStore delegate = mock(OfflinePlayerDataStore.class);
        doAnswer(invocation -> {
            Files.createDirectories(playerDataDirectory());
            Files.write(recoveryBackup(playerId), originalBytes);
            Files.write(playerFile(playerId), committedBytes);
            return null;
        }).when(delegate).save(
                org.mockito.ArgumentMatchers.any(OfflinePlayerData.class),
                org.mockito.ArgumentMatchers.any(InventoryKind.class),
                org.mockito.ArgumentMatchers.any(InventorySnapshot.class)
        );
        return delegate;
    }

    private static OfflinePlayerData data(
            UUID playerId,
            InventorySnapshot snapshot,
            byte[] bytes
    ) throws IOException {
        return new OfflinePlayerData(playerId, snapshot, revision(bytes));
    }

    private static PlayerDataRevision revision(byte[] bytes) throws IOException {
        try {
            return new PlayerDataRevision(HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)
            ));
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException(exception);
        }
    }

    private RecoverableOfflinePlayerDataStore store(OfflinePlayerDataStore delegate) {
        return new RecoverableOfflinePlayerDataStore(delegate, levelDirectory);
    }

    private Path playerDataDirectory() {
        return levelDirectory.resolve("players").resolve("data");
    }

    private Path playerFile(UUID playerId) {
        return playerDataDirectory().resolve(playerId + ".dat");
    }

    private Path migrationBackup(UUID playerId) {
        return playerDataDirectory().resolve(playerId + ".dat.invtools-migration-backup");
    }

    private Path recoveryBackup(UUID playerId) {
        return playerDataDirectory().resolve(playerId + ".dat.invtools-backup");
    }
}
