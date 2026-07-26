package nl.hauntedmc.serverfeatures.features.sanitize.internal.util;

import nl.hauntedmc.serverfeatures.util.TestFs;
import nl.hauntedmc.serverfeatures.util.TestPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeFsTest {

    @TempDir
    Path tmp;

    @Test
    void deleteRecursivelyReturnsFalseForNullInput() {
        assertFalse(SafeFs.deleteRecursively(null));
    }

    @Test
    void deleteRecursivelyReturnsTrueForMissingPath() {
        Path scenario = TestPaths.features(tmp, "sanitize", "safefs-missing");
        Path missing = scenario.resolve("missing-dir");

        assertTrue(SafeFs.deleteRecursively(missing));
    }

    @Test
    void deleteRecursivelyDeletesFileTree() throws Exception {
        Path scenario = TestPaths.features(tmp, "sanitize", "safefs-delete-tree");
        Path root = TestPaths.serverRoot(scenario).resolve("target");
        Path nested = Files.createDirectories(root.resolve("a").resolve("b"));
        TestFs.write(nested.resolve("x.txt"), "x");
        TestFs.write(root.resolve("top.txt"), "y");

        assertTrue(Files.exists(root.resolve("a").resolve("b").resolve("x.txt")));

        assertTrue(SafeFs.deleteRecursively(root));
        assertTrue(!Files.exists(root));
    }
}

