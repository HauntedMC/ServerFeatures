package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import nl.hauntedmc.serverfeatures.features.invtools.model.InventoryKind;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventorySnapshot;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        RecoverableOfflinePlayerDataStore store = new RecoverableOfflinePlayerDataStore(
                delegate,
                levelDirectory
        );
        Path backup = levelDirectory.resolve("players")
                .resolve("data")
                .resolve(playerId + ".dat.invtools-migration-backup");
        Files.createDirectories(backup.getParent());

        assertFalse(store.hasPlayerData(playerId));

        Files.write(backup, new byte[]{1});

        assertTrue(store.hasPlayerData(playerId));
    }

    @Test
    void symlinkMigrationBackupIsNotAccepted() throws IOException {
        UUID playerId = UUID.randomUUID();
        OfflinePlayerDataStore delegate = mock(OfflinePlayerDataStore.class);
        when(delegate.hasPlayerData(playerId)).thenReturn(false);
        RecoverableOfflinePlayerDataStore store = new RecoverableOfflinePlayerDataStore(
                delegate,
                levelDirectory
        );
        Path directory = levelDirectory.resolve("players").resolve("data");
        Path target = levelDirectory.resolve("outside.dat");
        Path backup = directory.resolve(playerId + ".dat.invtools-migration-backup");
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
    void rereadsAndVerifiesACommittedOfflineEdit() throws IOException {
        UUID playerId = UUID.randomUUID();
        OfflinePlayerDataStore delegate = mock(OfflinePlayerDataStore.class);
        InventorySnapshot originalSnapshot = InventorySnapshot.empty();
        InventorySnapshot changedSnapshot = originalSnapshot.withBackingSlot(
                InventoryKind.PLAYER,
                0,
                new ItemStack(Material.STONE, 3)
        );
        OfflinePlayerData original = new OfflinePlayerData(
                playerId,
                originalSnapshot,
                REVISION
        );
        when(delegate.load(playerId)).thenReturn(new OfflinePlayerData(
                playerId,
                changedSnapshot,
                REVISION
        ));
        RecoverableOfflinePlayerDataStore store = new RecoverableOfflinePlayerDataStore(
                delegate,
                levelDirectory
        );

        store.save(original, InventoryKind.PLAYER, changedSnapshot);

        verify(delegate).save(original, InventoryKind.PLAYER, changedSnapshot);
        verify(delegate).load(playerId);
    }

    @Test
    void mismatchedCommittedEditFailsClosed() throws IOException {
        UUID playerId = UUID.randomUUID();
        OfflinePlayerDataStore delegate = mock(OfflinePlayerDataStore.class);
        InventorySnapshot originalSnapshot = InventorySnapshot.empty();
        InventorySnapshot changedSnapshot = originalSnapshot.withBackingSlot(
                InventoryKind.ENDER_CHEST,
                0,
                new ItemStack(Material.DIAMOND, 2)
        );
        OfflinePlayerData original = new OfflinePlayerData(
                playerId,
                originalSnapshot,
                REVISION
        );
        when(delegate.load(playerId)).thenReturn(new OfflinePlayerData(
                playerId,
                originalSnapshot,
                REVISION
        ));
        RecoverableOfflinePlayerDataStore store = new RecoverableOfflinePlayerDataStore(
                delegate,
                levelDirectory
        );

        IOException exception = assertThrows(
                IOException.class,
                () -> store.save(original, InventoryKind.ENDER_CHEST, changedSnapshot)
        );

        assertTrue(exception.getMessage().contains("did not match"));
    }

    @Test
    void unreadableCommittedEditFailsClosed() throws IOException {
        UUID playerId = UUID.randomUUID();
        OfflinePlayerDataStore delegate = mock(OfflinePlayerDataStore.class);
        OfflinePlayerData original = new OfflinePlayerData(
                playerId,
                InventorySnapshot.empty(),
                REVISION
        );
        when(delegate.load(playerId)).thenThrow(new IOException("fixture read failure"));
        RecoverableOfflinePlayerDataStore store = new RecoverableOfflinePlayerDataStore(
                delegate,
                levelDirectory
        );

        IOException exception = assertThrows(
                IOException.class,
                () -> store.save(original, InventoryKind.PLAYER, InventorySnapshot.empty())
        );

        assertTrue(exception.getMessage().contains("recovery backup has been retained"));
    }
}
