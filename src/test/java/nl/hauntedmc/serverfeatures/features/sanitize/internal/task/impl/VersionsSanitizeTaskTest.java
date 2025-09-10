package nl.hauntedmc.serverfeatures.features.sanitize.internal.task.impl;

import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeContext;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeResult;
import nl.hauntedmc.serverfeatures.util.TestFs;
import nl.hauntedmc.serverfeatures.util.TestPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VersionsSanitizeTaskTest {

    @TempDir
    Path tmp;

    @Test
    void removesAllOtherVersionFoldersRecursivelyButKeepsCurrent() throws Exception {
        String version = "1.21.1";
        Path scenario = TestPaths.features(tmp, "sanitize", "versions-keep-current-remove-others");
        Path serverRoot = TestPaths.serverRoot(scenario);
        Path versions = TestPaths.dir(serverRoot, "versions");

        Path keepDir = Files.createDirectories(versions.resolve(version));
        TestFs.write(keepDir.resolve("server.jar"), "dummy keep");

        Path v1204 = Files.createDirectories(versions.resolve("1.20.4"));
        TestFs.write(v1204.resolve("old-server.jar"), "old");
        Files.createDirectories(v1204.resolve("plugins"));

        Path v119 = Files.createDirectories(versions.resolve("1.19"));
        TestFs.write(v119.resolve("README.txt"), "legacy");

        TestFs.write(versions.resolve("note.txt"), "hi");

        VersionsSanitizeTask task = new VersionsSanitizeTask();
        SanitizeContext ctx = new SanitizeContext(serverRoot, version);

        SanitizeResult result = task.run(ctx);

        assertTrue(result.changed());
        assertTrue(Files.exists(keepDir));
        assertFalse(Files.exists(v1204));
        assertFalse(Files.exists(v119));
        assertTrue(Files.exists(versions.resolve("note.txt")));
    }

    @Test
    void unchangedWhenOnlyKeepFolderPresent() throws Exception {
        String version = "1.21.1";
        Path scenario = TestPaths.features(tmp, "sanitize", "versions-only-keep");
        Path serverRoot = TestPaths.serverRoot(scenario);
        Path versions = TestPaths.dir(serverRoot, "versions");
        Files.createDirectories(versions.resolve(version));

        VersionsSanitizeTask task = new VersionsSanitizeTask();
        SanitizeContext ctx = new SanitizeContext(serverRoot, version);

        SanitizeResult result = task.run(ctx);

        assertFalse(result.changed());
    }

    @Test
    void unchangedWhenVersionsDirectoryMissing() throws Exception {
        String version = "1.21.1";
        Path scenario = TestPaths.features(tmp, "sanitize", "versions-missing-dir");
        Path serverRoot = TestPaths.serverRoot(scenario);

        VersionsSanitizeTask task = new VersionsSanitizeTask();
        SanitizeContext ctx = new SanitizeContext(serverRoot, version);

        SanitizeResult result = task.run(ctx);

        assertFalse(result.changed());
    }
}
