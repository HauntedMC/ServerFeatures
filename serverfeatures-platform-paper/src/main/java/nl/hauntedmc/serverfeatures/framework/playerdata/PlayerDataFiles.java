package nl.hauntedmc.serverfeatures.framework.playerdata;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * Resolves Paper-owned playerdata files without duplicating the active world layout.
 */
public final class PlayerDataFiles {

    private static final Path PLAYER_DATA_RELATIVE_PATH = Path.of("players", "data");

    private PlayerDataFiles() {
    }

    public static Path dataDirectory(Path levelDirectory) {
        Path normalizedLevelDirectory = Objects.requireNonNull(levelDirectory, "levelDirectory")
                .toAbsolutePath()
                .normalize();
        Path playerDataDirectory = normalizedLevelDirectory
                .resolve(PLAYER_DATA_RELATIVE_PATH)
                .normalize();
        if (!playerDataDirectory.startsWith(normalizedLevelDirectory)) {
            throw new IllegalArgumentException("Player data directory escaped the level directory");
        }
        return playerDataDirectory;
    }

    public static Path playerFile(Path levelDirectory, UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Path directory = dataDirectory(levelDirectory);
        Path file = directory.resolve(playerId + ".dat").normalize();
        if (!file.getParent().equals(directory)) {
            throw new IllegalArgumentException("Player data file escaped the player data directory");
        }
        return file;
    }
}
