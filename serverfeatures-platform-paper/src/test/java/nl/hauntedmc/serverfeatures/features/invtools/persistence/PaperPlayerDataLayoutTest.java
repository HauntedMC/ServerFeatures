package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PaperPlayerDataLayoutTest {

    @TempDir
    Path serverDirectory;

    @Test
    void resolvesPlayersDataBelowTheConfiguredLevelDirectory() {
        Path levelDirectory = serverDirectory.resolve("custom-default-world");
        Path resolved = PaperPlayerDataLayout.playerDataDirectory(levelDirectory);

        assertEquals(
                levelDirectory.toAbsolutePath().normalize().resolve("players").resolve("data"),
                resolved
        );
        assertNotEquals(
                levelDirectory.toAbsolutePath().normalize().resolve("playerdata"),
                resolved
        );
    }
}
