package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDataIdentityIndexTest {

    @TempDir
    Path playerDataDirectory;

    @Test
    void resolvesCanonicalForwardedUuidWithoutLocalNameMetadata() throws IOException {
        UUID canonicalPlayerId = createPlayerDataFile();
        PlayerDataIdentityIndex index = index(
                Map.of(),
                identifier -> Optional.of(new CanonicalPlayerIdentityLookup.Identity(
                        canonicalPlayerId,
                        "HauntedMC"
                ))
        );

        Optional<UUID> resolved = index.resolve(Optional.empty(), "hauntedmc");

        assertEquals(Optional.of(canonicalPlayerId), resolved);
    }

    @Test
    void canonicalIdentityOutranksPapersStaleCachedUuid() throws IOException {
        UUID staleCachedPlayerId = createPlayerDataFile();
        UUID canonicalPlayerId = createPlayerDataFile();
        PlayerDataIdentityIndex index = index(
                Map.of(staleCachedPlayerId, "HauntedMC"),
                identifier -> Optional.of(new CanonicalPlayerIdentityLookup.Identity(
                        canonicalPlayerId,
                        "HauntedMC"
                ))
        );

        Optional<UUID> resolved = index.resolve(
                Optional.of(staleCachedPlayerId),
                "HauntedMC"
        );

        assertEquals(Optional.of(canonicalPlayerId), resolved);
    }

    @Test
    void canonicalIdentityMustOwnPlayerdataOnThisServer() throws IOException {
        UUID registryOnlyPlayerId = UUID.randomUUID();
        UUID localLegacyPlayerId = createPlayerDataFile();
        PlayerDataIdentityIndex index = index(
                Map.of(localLegacyPlayerId, "HauntedMC"),
                identifier -> Optional.of(new CanonicalPlayerIdentityLookup.Identity(
                        registryOnlyPlayerId,
                        "HauntedMC"
                ))
        );

        Optional<UUID> resolved = index.resolve(Optional.empty(), "HauntedMC");

        assertEquals(Optional.of(localLegacyPlayerId), resolved);
    }

    @Test
    void canonicalLookupFailureIsNotReportedAsAnUnknownPlayer() {
        PlayerDataIdentityIndex index = index(
                Map.of(),
                identifier -> {
                    throw new IOException("DataRegistry unavailable");
                }
        );

        IOException failure = assertThrows(
                IOException.class,
                () -> index.resolve(Optional.empty(), "HauntedMC")
        );

        assertEquals("DataRegistry unavailable", failure.getMessage());
    }

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
    void acceptsAnExistingPreferredUuidWhenItsStoredNameMatches() throws IOException {
        UUID cachedPlayerId = createPlayerDataFile();
        PlayerDataIdentityIndex index = new PlayerDataIdentityIndex(
                playerDataDirectory,
                file -> "HauntedMC"
        );

        Optional<UUID> resolved = index.resolve(
                Optional.of(cachedPlayerId),
                "HauntedMC"
        );

        assertEquals(Optional.of(cachedPlayerId), resolved);
    }

    @Test
    void rejectsAStalePreferredUuidForAnotherPlayersData() throws IOException {
        UUID stalePlayerId = createPlayerDataFile();
        UUID requestedPlayerId = createPlayerDataFile();
        PlayerDataIdentityIndex index = index(Map.of(
                stalePlayerId, "AnotherPlayer",
                requestedPlayerId, "HauntedMC"
        ));

        Optional<UUID> resolved = index.resolve(Optional.of(stalePlayerId), "HauntedMC");

        assertEquals(Optional.of(requestedPlayerId), resolved);
    }

    @Test
    void resolvesARealPlayerdataFileFromPapersPersistedUserCache() throws IOException {
        UUID playerId = createPlayerDataFile();
        Path userCache = playerDataDirectory.getParent().resolve("usercache.json");
        Files.writeString(
                userCache,
                "[{\"name\":\"HauntedMC\",\"uuid\":\"" + playerId + "\"}]"
        );
        PlayerDataIdentityIndex index = new PlayerDataIdentityIndex(
                playerDataDirectory,
                file -> null,
                List.of(userCache),
                CanonicalPlayerIdentityLookup.none()
        );

        Optional<UUID> resolved = index.resolve(Optional.empty(), "HauntedMC");

        assertEquals(Optional.of(playerId), resolved);
    }

    @Test
    void ignoresMalformedUserCacheWithoutHidingPlayerdataMetadata() throws IOException {
        UUID playerId = createPlayerDataFile();
        Path userCache = playerDataDirectory.getParent().resolve("usercache.json");
        Files.writeString(userCache, "not valid json");
        PlayerDataIdentityIndex index = new PlayerDataIdentityIndex(
                playerDataDirectory,
                file -> playerId.equals(playerId(file)) ? "HauntedMC" : null,
                List.of(userCache),
                CanonicalPlayerIdentityLookup.none()
        );

        assertEquals(Optional.of(playerId), index.resolve(Optional.empty(), "HauntedMC"));
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
    void realConnectionOutranksAStaleCachedUuidThatStillHasPlayerdata() throws IOException {
        UUID staleCachedPlayerId = createPlayerDataFile();
        UUID connectedPlayerId = createPlayerDataFile();
        PlayerDataIdentityIndex index = index(Map.of());
        index.remember(connectedPlayerId, "HauntedMC");

        Optional<UUID> resolved = index.resolve(
                Optional.of(staleCachedPlayerId),
                "HauntedMC"
        );

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
        return index(playerNames, CanonicalPlayerIdentityLookup.none());
    }

    private PlayerDataIdentityIndex index(
            Map<UUID, String> playerNames,
            CanonicalPlayerIdentityLookup canonicalIdentityLookup
    ) {
        return new PlayerDataIdentityIndex(
                playerDataDirectory,
                file -> playerNames.get(playerId(file)),
                List.of(),
                canonicalIdentityLookup
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
