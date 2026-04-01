package nl.hauntedmc.serverfeatures.features.sanitize.internal.task.impl;

import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeContext;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeResult;
import nl.hauntedmc.serverfeatures.util.TestFs;
import nl.hauntedmc.serverfeatures.util.TestPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultConfigsSanitizeTaskTest {

    @TempDir
    Path tmp;

    @Test
    void createsMissingDefaultConfigFilesAndIsIdempotent() throws Exception {
        Path scenario = TestPaths.features(tmp, "sanitize", "default-configs-create");
        Path serverRoot = TestPaths.serverRoot(scenario);
        DefaultConfigsSanitizeTask task = new DefaultConfigsSanitizeTask();
        SanitizeContext ctx = new SanitizeContext(serverRoot, "1.21.1");

        SanitizeResult first = task.run(ctx);
        SanitizeResult second = task.run(ctx);

        assertTrue(first.changed());
        assertFalse(second.changed());

        assertTrue(Files.exists(serverRoot.resolve("eula.txt")));
        assertTrue(Files.exists(serverRoot.resolve("permissions.yml")));
        assertTrue(Files.exists(serverRoot.resolve("ops.json")));
    }

    @Test
    void updatesFileWhenContentDiffersIgnoringTrailingWhitespace() throws Exception {
        Path scenario = TestPaths.features(tmp, "sanitize", "default-configs-update");
        Path serverRoot = TestPaths.serverRoot(scenario);
        TestFs.write(serverRoot.resolve("eula.txt"), "eula=false");

        DefaultConfigsSanitizeTask task = new DefaultConfigsSanitizeTask();
        SanitizeContext ctx = new SanitizeContext(serverRoot, "1.21.1");

        SanitizeResult result = task.run(ctx);

        assertTrue(result.changed());
        assertTrue(Files.readString(serverRoot.resolve("eula.txt")).contains("eula=true"));
    }
}

