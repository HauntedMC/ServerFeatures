package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbtOfflinePlayerDataStoreTest {

    @TempDir
    Path levelDirectory;

    @Test
    void onlyARegularPrimaryWorldPlayerdataFileCountsAsPlayedHere() throws IOException {
        UUID playerId = UUID.randomUUID();
        NbtOfflinePlayerDataStore store = new NbtOfflinePlayerDataStore(levelDirectory, 4903);
        Path playerData = levelDirectory.resolve("playerdata").resolve(playerId + ".dat");

        assertFalse(store.hasPlayerData(playerId));

        Files.createDirectories(playerData.getParent());
        Files.createDirectory(playerData);
        assertFalse(store.hasPlayerData(playerId));

        Files.delete(playerData);
        Files.write(playerData, new byte[]{1});
        assertTrue(store.hasPlayerData(playerId));
    }

    @Test
    void rejectsCompressedPlayerdataThatExpandsBeyondTheSafeLimit() throws IOException {
        UUID playerId = UUID.randomUUID();
        NbtOfflinePlayerDataStore store = new NbtOfflinePlayerDataStore(levelDirectory, 4903);
        Path playerData = levelDirectory.resolve("playerdata").resolve(playerId + ".dat");
        Files.createDirectories(playerData.getParent());

        byte[] chunk = new byte[8192];
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(playerData))) {
            for (int index = 0; index < 4097; index++) {
                output.write(chunk);
            }
        }

        IOException exception = assertThrows(IOException.class, () -> store.load(playerId));
        assertTrue(exception.getMessage().contains("expands beyond the safe read limit"));
    }

    @Test
    void derivesPapersCurrentOnlineModeFallbackUuid() {
        String playerName = "LegacyPlayer";
        UUID offlineModePlayerId = UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName)
                .getBytes(StandardCharsets.UTF_8));

        assertEquals(offlineModePlayerId, NbtOfflinePlayerDataStore.offlineModePlayerId(playerName));
    }
}
