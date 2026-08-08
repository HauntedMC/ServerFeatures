package nl.hauntedmc.serverfeatures.features.playerdata.inspect;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import nl.hauntedmc.serverfeatures.framework.playerdata.PlayerDataFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

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

        ReadWriteNBT root = NBT.createNBTObject();
        root.setInteger("DataVersion", 4444);
        root.getOrCreateCompound("bukkit").setString("lastKnownName", "TestPlayer");
        root.getOrCreateCompound("BukkitValues")
                .setByte("serverfeatures:autopickup_enabled", (byte) 1);
        root.getOrCreateCompound("BukkitValues")
                .setString("otherplugin:setting", "hidden");
        NBT.writeFile(PlayerDataFiles.playerFile(level, playerId).toFile(), root);

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
    void browsesCompoundPathsWithoutMutatingPlayerdata() throws IOException {
        UUID playerId = UUID.randomUUID();
        Path level = temporaryDirectory.resolve("level-two");
        Files.createDirectories(PlayerDataFiles.dataDirectory(level));

        ReadWriteNBT root = NBT.createNBTObject();
        root.setInteger("DataVersion", 4444);
        root.getOrCreateCompound("bukkit").setString("lastKnownName", "PathTester");
        root.getOrCreateCompound("abilities").setFloat("flySpeed", 0.05F);
        Path file = PlayerDataFiles.playerFile(level, playerId);
        NBT.writeFile(file.toFile(), root);
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
        assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(file)));
    }
}
