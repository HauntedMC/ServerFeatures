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

class BukkitYmlSanitizeTaskTest {

    @TempDir
    Path tmp;

    @Test
    void rewritesBukkitYmlAndPreservesUnknownTopLevelKeysInFooter() throws Exception {
        Path scenario = TestPaths.features(tmp, "sanitize", "bukkit-yml-rewrite");
        Path serverRoot = TestPaths.serverRoot(scenario);
        Path file = serverRoot.resolve("bukkit.yml");
        TestFs.write(file, """
                settings:
                  use-map-color-cache: false
                spawn-limits:
                  monsters: 70
                custom-root:
                  enabled: true
                """);

        BukkitYmlSanitizeTask task = new BukkitYmlSanitizeTask();
        SanitizeContext ctx = new SanitizeContext(serverRoot, "1.21.1");

        SanitizeResult first = task.run(ctx);
        SanitizeResult second = task.run(ctx);
        String out = Files.readString(file);

        assertTrue(first.changed());
        assertFalse(second.changed());
        assertTrue(out.contains("# Managed by HauntedMC Sanitize"));
        assertTrue(out.contains("spawn-limits:"));
        assertTrue(out.contains("## --- new/deprecated/other (detected) ---"));
        assertTrue(out.contains("# - custom-root"));
    }
}

