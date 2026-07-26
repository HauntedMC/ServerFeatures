package nl.hauntedmc.serverfeatures.features.backup.internal.util;

import nl.hauntedmc.serverfeatures.util.TestFs;
import nl.hauntedmc.serverfeatures.util.TestPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipUtilTest {

    @TempDir
    Path tmp;

    @Test
    void zipsFilesFromDirectoriesAndStandaloneInputs() throws Exception {
        Path scenario = TestPaths.features(tmp, "backup", "zip-util");
        Path root = TestPaths.serverRoot(scenario);
        Path dir = TestPaths.dir(root, "data");
        TestFs.write(dir.resolve("a.txt"), "hello");
        TestFs.write(dir.resolve("nested").resolve("b.txt"), "world");
        Path single = TestFs.write(root.resolve("single.txt"), "one");

        Path zip = root.resolve("out").resolve("backup.zip");
        long[] counters = ZipUtil.zipPaths(zip, root, List.of(dir, single), 6);

        assertEquals(3L, counters[0]);
        assertEquals(13L, counters[1]);

        List<String> entries = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(java.nio.file.Files.newInputStream(zip))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
        }
        assertTrue(entries.contains("data/a.txt"));
        assertTrue(entries.contains("data/nested/b.txt"));
        assertTrue(entries.contains("single.txt"));
    }

    @Test
    void invalidCompressionLevelFallsBackWithoutFailing() throws Exception {
        Path scenario = TestPaths.features(tmp, "backup", "zip-util-level");
        Path root = TestPaths.serverRoot(scenario);
        Path input = TestFs.write(root.resolve("x.txt"), "x");
        Path zip = root.resolve("out.zip");

        long[] counters = ZipUtil.zipPaths(zip, root, List.of(input), 99);

        assertEquals(1L, counters[0]);
        assertEquals(1L, counters[1]);
        assertTrue(java.nio.file.Files.exists(zip));
    }
}

