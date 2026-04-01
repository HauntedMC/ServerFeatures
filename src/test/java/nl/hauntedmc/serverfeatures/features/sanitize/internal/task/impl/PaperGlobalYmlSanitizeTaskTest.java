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

class PaperGlobalYmlSanitizeTaskTest {

    @TempDir
    Path tmp;

    @Test
    void rewritesPaperGlobalYmlAndAnnotatesControlledEntries() throws Exception {
        Path scenario = TestPaths.features(tmp, "sanitize", "paper-global-yml-rewrite");
        Path serverRoot = TestPaths.serverRoot(scenario);
        Path file = serverRoot.resolve("config").resolve("paper-global.yml");
        TestFs.write(file, """
                collisions:
                  enable-player-collisions: true
                unknown-root:
                  enabled: true
                """);

        PaperGlobalYmlSanitizeTask task = new PaperGlobalYmlSanitizeTask();
        SanitizeContext ctx = new SanitizeContext(serverRoot, "1.21.1");

        SanitizeResult first = task.run(ctx);
        SanitizeResult second = task.run(ctx);
        String out = Files.readString(file);

        assertTrue(first.changed());
        assertFalse(second.changed());
        assertTrue(out.contains("# Managed by HauntedMC Sanitize (paper-global.yml)"));
        assertTrue(out.contains("# - unknown-root"));
        assertTrue(out.contains("# controlled by Sanitize"));
    }
}

