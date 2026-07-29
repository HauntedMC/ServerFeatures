package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDataIdentityIndexTest {

    @TempDir
    Path playerDataDirectory;

    @Test
    void resolvesTheActualLocalFileWhenPapersCachedUuidHasNoData() throws IOException {
        UUID cachedPlayerId = UUID.randomUUID();
        UUID localPlayerId = createPlayerDataFile();
        PlayerDataIdentityIndex index = index(Map.of(localPlayerId, "HauntedMC"));

        Optional<UUID> resolved = index.resolve(
                Optional.of(cachedPlayerId),
                "hauntedmc"
        );

        assertEquals(Optional.of(localPlayerId), resolved);
    }

    @Test
    void acceptsAnExistingPreferredUuidWithoutScanningPlayerdata() throws IOException {
        UUID cachedPlayerId = createPlayerDataFile();
        PlayerDataIdentityIndex index = new PlayerDataIdentityIndex(
                playerDataDirectory,
                file -> {
                    throw new AssertionError("The local index should not be scanned");
                }
        );

        Optional<UUID> resolved = index.resolve(
                Optional.of(cachedPlayerId),
                "HauntedMC"
        );

        assertEquals(Optional.of(cachedPlayerId), resolved);
    }

    @Test
    void choosesTheNewestFileWhenANameExistsUnderMultipleUuids() throws IOException {
        UUID oldPlayerId = createPlayerDataFile();
        UUID currentPlayerId = createPlayerDataFile();
        Files.setLastModifiedTime(
                playerDataFile(oldPlayerId),
                FileTime.from(Instant.parse("2026-01-01T00:00:00Z"))
        );
        Files.setLastModifiedTime(
                playerDataFile(currentPlayerId),
                FileTime.from(Instant.parse("2026-02-01T00:00:00Z"))
        );
        PlayerDataIdentityIndex index = index(Map.of(
                oldPlayerId, "HauntedMC",
                currentPlayerId, "HauntedMC"
        ));

        Optional<UUID> resolved = index.resolve(Optional.empty(), "HauntedMC");

        assertEquals(Optional.of(currentPlayerId), resolved);
    }

    @Test
    void remembersTheIdentityObservedDuringARealConnection() throws IOException {
        UUID connectedPlayerId = createPlayerDataFile();
        PlayerDataIdentityIndex index = new PlayerDataIdentityIndex(
                playerDataDirectory,
                file -> {
                    throw new AssertionError("The local index should not be scanned");
                }
        );
        index.remember(connectedPlayerId, "HauntedMC");

        Optional<UUID> resolved = index.resolve(Optional.empty(), "hauntedmc");

        assertEquals(Optional.of(connectedPlayerId), resolved);
    }

    @Test
    void forgetsAPlayersPreviousNameAfterARealConnection() throws IOException {
        UUID connectedPlayerId = createPlayerDataFile();
        PlayerDataIdentityIndex index = index(Map.of());
        index.remember(connectedPlayerId, "OldName");
        index.remember(connectedPlayerId, "CurrentName");

        assertTrue(index.resolve(Optional.empty(), "OldName").isEmpty());
        assertEquals(
                Optional.of(connectedPlayerId),
                index.resolve(Optional.empty(), "CurrentName")
        );
    }

    @Test
    void skipsAnUnreadableFileWithoutHidingOtherPlayers() throws IOException {
        UUID corruptPlayerId = createPlayerDataFile();
        UUID validPlayerId = createPlayerDataFile();
        PlayerDataIdentityIndex index = new PlayerDataIdentityIndex(
                playerDataDirectory,
                file -> {
                    UUID playerId = playerId(file);
                    if (playerId.equals(corruptPlayerId)) {
                        throw new IOException("Corrupt playerdata");
                    }
                    return playerId.equals(validPlayerId) ? "HauntedMC" : null;
                }
        );

        assertEquals(
                Optional.of(validPlayerId),
                index.resolve(Optional.empty(), "HauntedMC")
        );
    }

    @Test
    void returnsEmptyWhenNoMatchingLocalPlayerdataExists() throws IOException {
        PlayerDataIdentityIndex index = index(Map.of());

        assertTrue(index.resolve(Optional.of(UUID.randomUUID()), "Missing").isEmpty());
    }

    private PlayerDataIdentityIndex index(Map<UUID, String> playerNames) {
        return new PlayerDataIdentityIndex(
                playerDataDirectory,
                file -> playerNames.get(playerId(file))
        );
    }

    private UUID createPlayerDataFile() throws IOException {
        UUID playerId = UUID.randomUUID();
        Files.write(playerDataFile(playerId), new byte[]{1});
        return playerId;
    }

    private Path playerDataFile(UUID playerId) {
        return playerDataDirectory.resolve(playerId + ".dat");
    }

    private static UUID playerId(Path playerDataFile) {
        String fileName = playerDataFile.getFileName().toString();
        return UUID.fromString(fileName.substring(0, fileName.length() - 4));
    }
}
