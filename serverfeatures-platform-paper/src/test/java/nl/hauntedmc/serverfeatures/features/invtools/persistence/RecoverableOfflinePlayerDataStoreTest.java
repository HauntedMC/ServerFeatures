package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecoverableOfflinePlayerDataStoreTest {

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
}
