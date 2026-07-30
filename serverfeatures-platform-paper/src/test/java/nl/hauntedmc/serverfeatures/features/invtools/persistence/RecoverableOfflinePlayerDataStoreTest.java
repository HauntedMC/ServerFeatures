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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecoverableOfflinePlayerDataStoreTest {

    private static final PlayerDataRevision REVISION = new PlayerDataRevision("0".repeat(64));

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
        OfflinePlayerDataStore delegate = mock(OfflinePlayerDataStore.class);
        InventorySnapshot changed = InventorySnapshot.empty().withBackingSlot(
                InventoryKind.PLAYER,
                9,
                TestItemStacks.item(Material.DIAMOND, 3)
        );
        OfflinePlayerData original = new OfflinePlayerData(
                playerId,
                InventorySnapshot.empty(),
                REVISION
        );
        when(delegate.load(playerId)).thenReturn(new OfflinePlayerData(playerId, changed, REVISION));
        Path backup = recoveryBackup(playerId);
        Files.createDirectories(backup.getParent());
        Files.write(backup, new byte[]{7, 8, 9});
        RecoverableOfflinePlayerDataStore store = store(delegate);

        store.save(original, InventoryKind.PLAYER, changed);

        verify(delegate).save(original, InventoryKind.PLAYER, changed);
        verify(delegate).load(playerId);
        assertFalse(Files.exists(backup));
    }

    @Test
    void semanticVerificationMismatchRetainsOrdinaryRecoveryBackup() throws IOException {
        UUID playerId = UUID.randomUUID();
        OfflinePlayerDataStore delegate = mock(OfflinePlayerDataStore.class);
        InventorySnapshot changed = InventorySnapshot.empty().withBackingSlot(
                InventoryKind.ENDER_CHEST,
                4,
                TestItemStacks.item(Material.EMERALD, 2)
        );
        OfflinePlayerData original = new OfflinePlayerData(
                playerId,
                InventorySnapshot.empty(),
                REVISION
        );
        when(delegate.load(playerId)).thenReturn(new OfflinePlayerData(
                playerId,
                InventorySnapshot.empty(),
                REVISION
        ));
        Path backup = recoveryBackup(playerId);
        Files.createDirectories(backup.getParent());
        Files.write(backup, new byte[]{3, 2, 1});
        RecoverableOfflinePlayerDataStore store = store(delegate);

        store.save(original, InventoryKind.ENDER_CHEST, changed);

        assertTrue(Files.isRegularFile(backup));
    }

    @Test
    void validLoadRemovesAStaleOrdinaryRecoveryBackup() throws IOException {
        UUID playerId = UUID.randomUUID();
        OfflinePlayerDataStore delegate = mock(OfflinePlayerDataStore.class);
        OfflinePlayerData loaded = new OfflinePlayerData(
                playerId,
                InventorySnapshot.empty(),
                REVISION
        );
        when(delegate.load(playerId)).thenReturn(loaded);
        Path target = playerFile(playerId);
        Path backup = recoveryBackup(playerId);
        Files.createDirectories(target.getParent());
        Files.write(target, new byte[]{4});
        Files.write(backup, new byte[]{5});
        RecoverableOfflinePlayerDataStore store = store(delegate);

        assertTrue(store.load(playerId) == loaded);

        assertFalse(Files.exists(backup));
    }

    @Test
    void missingPrimaryIsRestoredFromOrdinaryRecoveryBackupBeforeLoad() throws IOException {
        UUID playerId = UUID.randomUUID();
        OfflinePlayerDataStore delegate = mock(OfflinePlayerDataStore.class);
        OfflinePlayerData loaded = new OfflinePlayerData(
                playerId,
                InventorySnapshot.empty(),
                REVISION
        );
        byte[] backupBytes = new byte[]{9, 8, 7, 6};
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
