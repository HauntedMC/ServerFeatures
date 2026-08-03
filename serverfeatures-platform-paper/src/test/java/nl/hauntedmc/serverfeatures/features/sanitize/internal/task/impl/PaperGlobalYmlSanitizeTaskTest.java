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
                _version: 31
                anticheat:
                  obfuscation:
                    items:
                      enable-item-obfuscation: true
                chunk-loading-basic:
                  player-max-chunk-generate-rate: 12.0
                collisions:
                  enable-player-collisions: true
                commands:
                  suggest-player-names-when-null-tab-completions: true
                  time-command-affects-all-worlds: true
                misc:
                  chat-threads:
                    chat-executor-max-size: 8
                  compression-level: 3
                  region-file-cache-size: 64
                packet-limiter:
                  all-packets:
                    action: DROP
                    interval: 1.0
                    max-packet-rate: 1.0
                player-auto-save:
                  max-per-tick: 3
                  rate: 100
                time:
                  affects-all-worlds: true
                unknown-root:
                  enabled: true
                unsupported-settings:
                  oversized-item-component-sanitizer:
                    dont-sanitize:
                     - minecraft:container
                update-checker:
                  enabled: false
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
        assertFalse(out.contains("# - _version"));
        assertFalse(out.contains("# - packet-limiter"));
        assertFalse(out.contains("# - time"));
        assertFalse(out.contains("# - update-checker"));
        assertTrue(out.contains("# controlled by Sanitize"));
        assertTrue(out.contains("suggest-player-names-when-null-tab-completions: false # controlled by Sanitize"));
        assertFalse(out.contains("time-command-affects-all-worlds"));
        assertTrue(out.contains("affects-all-worlds: false # controlled by Sanitize"));
        assertTrue(out.contains("action: KICK # controlled by Sanitize"));
        assertTrue(out.contains("max-packet-rate: 500.0 # controlled by Sanitize"));
        assertTrue(out.contains("kick-message: <red><lang:disconnect.exceeded_packet_rate> # controlled by Sanitize"));
        assertTrue(out.contains("enabled: true # controlled by Sanitize"));
        assertTrue(out.contains("dont-sanitize: [ # controlled by Sanitize"));
        assertTrue(out.contains("enable-item-obfuscation: true"));
        assertFalse(out.contains("enable-item-obfuscation: true # controlled by Sanitize"));
        assertTrue(out.contains("player-max-chunk-generate-rate: 12.0"));
        assertFalse(out.contains("player-max-chunk-generate-rate: 12.0 # controlled by Sanitize"));
        assertTrue(out.contains("chat-executor-max-size: 8"));
        assertTrue(out.contains("compression-level: 3"));
        assertTrue(out.contains("region-file-cache-size: 64"));
        assertTrue(out.contains("max-per-tick: 3"));
        assertTrue(out.contains("rate: 100"));
        assertFalse(out.contains("secret:"));
    }

    @Test
    void preservesManuallyConfiguredVelocitySecret() throws Exception {
        Path scenario = TestPaths.features(tmp, "sanitize", "paper-global-yml-manual-velocity-secret");
        Path serverRoot = TestPaths.serverRoot(scenario);
        Path file = serverRoot.resolve("config").resolve("paper-global.yml");
        TestFs.write(file, """
                proxies:
                  velocity:
                    secret: manually-configured-secret
                """);

        PaperGlobalYmlSanitizeTask task = new PaperGlobalYmlSanitizeTask();
        SanitizeContext ctx = new SanitizeContext(serverRoot, "1.21.1");

        SanitizeResult first = task.run(ctx);
        SanitizeResult second = task.run(ctx);
        String out = Files.readString(file);

        assertTrue(first.changed());
        assertFalse(second.changed());
        assertTrue(out.contains("secret: manually-configured-secret"));
        assertFalse(out.contains("secret: manually-configured-secret # controlled by Sanitize"));
    }
}
