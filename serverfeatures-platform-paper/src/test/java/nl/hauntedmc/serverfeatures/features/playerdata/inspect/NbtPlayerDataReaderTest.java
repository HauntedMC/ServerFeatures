package nl.hauntedmc.serverfeatures.features.playerdata.inspect;

import nl.hauntedmc.serverfeatures.framework.playerdata.PlayerDataFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbtPlayerDataReaderTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesOfflinePlayerAndShowsServerFeaturesSettings() throws IOException {
        UUID playerId = UUID.randomUUID();
        Path level = temporaryDirectory.resolve("world");
        Path playerDataDirectory = PlayerDataFiles.dataDirectory(level);
        Files.createDirectories(playerDataDirectory);

        Path file = PlayerDataFiles.playerFile(level, playerId);
        writePlayerData(file, "TestPlayer", output -> {
            writeCompoundStart(output, "BukkitValues");
            writeByte(output, "serverfeatures:autopickup_enabled", (byte) 1);
            writeString(output, "otherplugin:setting", "hidden");
            writeEnd(output);
        });

        NbtPlayerDataReader reader = new NbtPlayerDataReader(
                level,
                4 * 1024 * 1024,
                32 * 1024 * 1024
        );
        NbtPlayerDataReader.ResolvedPlayerData resolved = reader.resolve("TestPlayer", null).orElseThrow();
        NbtPlayerDataReader.Inspection settings = reader.inspectSettings(resolved, 100, 240);

        assertEquals(playerId, resolved.playerId());
        assertEquals("TestPlayer", resolved.playerName());
        assertEquals(1, settings.totalEntries());
        assertEquals("serverfeatures:autopickup_enabled", settings.entries().getFirst().key());
        assertEquals("1 (true)", settings.entries().getFirst().value());
    }

    @Test
    void readsServerFeaturesSettingsFromOldPlayerdataWithoutConvertingIt() throws IOException {
        UUID playerId = UUID.randomUUID();
        Path level = temporaryDirectory.resolve("old-version");
        Files.createDirectories(PlayerDataFiles.dataDirectory(level));

        Path file = PlayerDataFiles.playerFile(level, playerId);
        writePlayerData(file, "LegacyPlayer", 100, output -> {
            writeCompoundStart(output, "BukkitValues");
            writeByte(output, "serverfeatures:autopickup_enabled", (byte) 0);
            writeByte(output, "serverfeatures:fairperks_fly_enabled", (byte) 1);
            writeEnd(output);
        });
        byte[] before = Files.readAllBytes(file);

        NbtPlayerDataReader reader = new NbtPlayerDataReader(
                level,
                4 * 1024 * 1024,
                32 * 1024 * 1024
        );
        NbtPlayerDataReader.ResolvedPlayerData resolved = reader.resolve("LegacyPlayer", playerId).orElseThrow();
        NbtPlayerDataReader.Inspection overview = reader.inspectOverview(resolved, 240);
        NbtPlayerDataReader.Inspection settings = reader.inspectSettings(resolved, 100, 240);

        assertEquals("100", overview.entries().stream()
                .filter(entry -> entry.key().equals("data-version"))
                .findFirst()
                .orElseThrow()
                .value());
        assertEquals(2, settings.totalEntries());
        assertEquals("0 (false)", settings.entries().getFirst().value());
        assertEquals("1 (true)", settings.entries().get(1).value());
        assertArrayEquals(before, Files.readAllBytes(file));
    }

    @Test
    void browsesCompoundPathsWithoutMutatingPlayerdata() throws IOException {
        UUID playerId = UUID.randomUUID();
        Path level = temporaryDirectory.resolve("level-two");
        Files.createDirectories(PlayerDataFiles.dataDirectory(level));

        Path file = PlayerDataFiles.playerFile(level, playerId);
        writePlayerData(file, "PathTester", output -> {
            writeCompoundStart(output, "abilities");
            writeFloat(output, "flySpeed", 0.05F);
            writeEnd(output);
        });
        byte[] before = Files.readAllBytes(file);

        NbtPlayerDataReader reader = new NbtPlayerDataReader(
                level,
                4 * 1024 * 1024,
                32 * 1024 * 1024
        );
        NbtPlayerDataReader.ResolvedPlayerData resolved = reader.resolve(playerId.toString(), null).orElseThrow();
        NbtPlayerDataReader.Inspection inspection = reader.inspectNbt(resolved, "abilities", 100, 240);

        assertEquals(1, inspection.totalEntries());
        assertEquals("flySpeed", inspection.entries().getFirst().key());
        assertTrue(inspection.entries().getFirst().value().startsWith("0.05"));
        assertArrayEquals(before, Files.readAllBytes(file));
    }

    private static void writePlayerData(Path file, String playerName, NbtWriter extraTags) throws IOException {
        writePlayerData(file, playerName, 4444, extraTags);
    }

    private static void writePlayerData(
            Path file,
            String playerName,
            int dataVersion,
            NbtWriter extraTags
    ) throws IOException {
        try (OutputStream fileOutput = Files.newOutputStream(file);
             GZIPOutputStream gzipOutput = new GZIPOutputStream(fileOutput);
             DataOutputStream output = new DataOutputStream(gzipOutput)) {
            writeCompoundStart(output, "");
            writeInt(output, "DataVersion", dataVersion);
            writeCompoundStart(output, "bukkit");
            writeString(output, "lastKnownName", playerName);
            writeEnd(output);
            extraTags.write(output);
            writeEnd(output);
        }
    }

    private static void writeCompoundStart(DataOutputStream output, String name) throws IOException {
        writeHeader(output, 10, name);
    }

    private static void writeByte(DataOutputStream output, String name, byte value) throws IOException {
        writeHeader(output, 1, name);
        output.writeByte(value);
    }

    private static void writeInt(DataOutputStream output, String name, int value) throws IOException {
        writeHeader(output, 3, name);
        output.writeInt(value);
    }

    private static void writeFloat(DataOutputStream output, String name, float value) throws IOException {
        writeHeader(output, 5, name);
        output.writeFloat(value);
    }

    private static void writeString(DataOutputStream output, String name, String value) throws IOException {
        writeHeader(output, 8, name);
        output.writeUTF(value);
    }

    private static void writeHeader(DataOutputStream output, int type, String name) throws IOException {
        output.writeByte(type);
        output.writeUTF(name);
    }

    private static void writeEnd(DataOutputStream output) throws IOException {
        output.writeByte(0);
    }

    @FunctionalInterface
    private interface NbtWriter {
        void write(DataOutputStream output) throws IOException;
    }
}
