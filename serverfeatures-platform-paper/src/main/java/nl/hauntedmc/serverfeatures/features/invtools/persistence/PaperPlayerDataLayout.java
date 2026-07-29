package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Resolves server-owned player storage for Paper's 26.x world layout.
 *
 * <p>Since Paper 26.1, player records are stored below {@code <level>/players/data}; they are no
 * longer stored in the pre-26.1 {@code <level>/playerdata} directory. Keeping this in one class
 * prevents persistence code and tests from independently guessing the on-disk layout.</p>
 */
final class PaperPlayerDataLayout {

    private static final Path PLAYER_DATA_RELATIVE_PATH = Path.of("players", "data");

    private PaperPlayerDataLayout() {
    }

    static Path playerDataDirectory(Path levelDirectory) {
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
}
