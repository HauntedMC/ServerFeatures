package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbtOfflinePlayerDataStoreTest {

    @TempDir
    Path levelDirectory;

    @Test
    void onlyARegularPrimaryWorldPlayerdataFileCountsAsPlayedHere() throws IOException {
        UUID playerId = UUID.randomUUID();
        NbtOfflinePlayerDataStore store = new NbtOfflinePlayerDataStore(levelDirectory);
        Path playerData = levelDirectory.resolve("playerdata").resolve(playerId + ".dat");

        assertFalse(store.hasPlayerData(playerId));

        Files.createDirectories(playerData.getParent());
        Files.createDirectory(playerData);
        assertFalse(store.hasPlayerData(playerId));

        Files.delete(playerData);
        Files.write(playerData, new byte[]{1});
        assertTrue(store.hasPlayerData(playerId));
    }
}
