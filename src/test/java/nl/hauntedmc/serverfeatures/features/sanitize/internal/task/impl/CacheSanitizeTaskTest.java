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

class CacheSanitizeTaskTest {

    @TempDir
    Path tmp;

    @Test
    void removesOtherMojangJarsButKeepsMatchingVersion() throws Exception {
        String version = "1.21.1";
        Path scenario = TestPaths.features(tmp, "sanitize", "cache-keep-matching-remove-others");
        Path serverRoot = TestPaths.serverRoot(scenario);
        Path cache = TestPaths.dir(serverRoot, "cache");

        Path keep = TestFs.touch(cache.resolve("mojang_" + version + ".jar"));
        Path old1 = TestFs.touch(cache.resolve("mojang_1.20.4.jar"));
        Path old2 = TestFs.touch(cache.resolve("mojang_1.19.2.jar"));

        Path paper = TestFs.touch(cache.resolve("paperclip.jar"));
        Path readme = TestFs.touch(cache.resolve("readme.txt"));

        CacheSanitizeTask task = new CacheSanitizeTask();
        SanitizeContext ctx = new SanitizeContext(serverRoot, version);

        SanitizeResult result = task.run(ctx);

        assertTrue(result.changed());
        assertTrue(Files.exists(keep));
        assertFalse(Files.exists(old1));
        assertFalse(Files.exists(old2));
        assertTrue(Files.exists(paper));
        assertTrue(Files.exists(readme));
    }

    @Test
    void unchangedWhenOnlyKeepJarPresent() throws Exception {
        String version = "1.21.1";
        Path scenario = TestPaths.features(tmp, "sanitize", "cache-only-keep");
        Path serverRoot = TestPaths.serverRoot(scenario);
        Path cache = TestPaths.dir(serverRoot, "cache");

        TestFs.touch(cache.resolve("mojang_" + version + ".jar"));

        CacheSanitizeTask task = new CacheSanitizeTask();
        SanitizeContext ctx = new SanitizeContext(serverRoot, version);

        SanitizeResult result = task.run(ctx);

        assertFalse(result.changed());
    }

    @Test
    void unchangedWhenCacheDirectoryMissing() throws Exception {
        String version = "1.21.1";
        Path scenario = TestPaths.features(tmp, "sanitize", "cache-missing-dir");
        Path serverRoot = TestPaths.serverRoot(scenario);

        CacheSanitizeTask task = new CacheSanitizeTask();
        SanitizeContext ctx = new SanitizeContext(serverRoot, version);

        SanitizeResult result = task.run(ctx);

        assertFalse(result.changed());
    }
}
