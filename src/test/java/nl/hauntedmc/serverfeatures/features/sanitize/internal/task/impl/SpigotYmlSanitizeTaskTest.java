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

class SpigotYmlSanitizeTaskTest {

    @TempDir
    Path tmp;

    @Test
    void rewritesSpigotYmlWithHeaderAndRemainsStableOnSecondRun() throws Exception {
        Path scenario = TestPaths.features(tmp, "sanitize", "spigot-yml-rewrite");
        Path serverRoot = TestPaths.serverRoot(scenario);
        Path file = serverRoot.resolve("spigot.yml");
        TestFs.write(file, """
                messages:
                  whitelist: test
                custom-root:
                  x: 1
                """);

        SpigotYmlSanitizeTask task = new SpigotYmlSanitizeTask();
        SanitizeContext ctx = new SanitizeContext(serverRoot, "1.21.1");

        SanitizeResult first = task.run(ctx);
        SanitizeResult second = task.run(ctx);
        String out = Files.readString(file);

        assertTrue(first.changed());
        assertFalse(second.changed());
        assertTrue(out.contains("# Managed by HauntedMC Sanitize"));
        assertTrue(out.contains("# - custom-root"));
        assertTrue(out.contains("messages:"));
        assertTrue(out.contains("commands:"));
        assertTrue(out.contains("settings:"));
    }
}

