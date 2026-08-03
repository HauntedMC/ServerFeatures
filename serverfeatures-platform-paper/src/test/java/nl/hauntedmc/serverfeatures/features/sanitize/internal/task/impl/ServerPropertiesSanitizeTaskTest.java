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

class ServerPropertiesSanitizeTaskTest {

    @TempDir
    Path tmp;

    @Test
    void rewritesIntoGroupedLayoutAndIsIdempotent() throws Exception {
        Path scenario = TestPaths.features(tmp, "sanitize", "server-properties-grouping");
        Path serverRoot = TestPaths.serverRoot(scenario);
        Path file = serverRoot.resolve("server.properties");
        TestFs.write(file, """
                # old file
                custom-key = abc
                difficulty: hard
                enable-code-of-conduct=true
                enable-status=true
                function-permission-level=2
                hide-online-players=false
                max-players=42
                max-tick-time=-1
                management-server-enabled=true
                op-permission-level=4
                custom-key=override
                """);

        ServerPropertiesSanitizeTask task = new ServerPropertiesSanitizeTask();
        SanitizeContext ctx = new SanitizeContext(serverRoot, "1.21.1");

        SanitizeResult first = task.run(ctx);
        SanitizeResult second = task.run(ctx);
        String rewritten = Files.readString(file);

        assertTrue(first.changed());
        assertFalse(second.changed());

        assertTrue(rewritten.contains("## --- new/deprecated/other (preserved as-is) ---"));
        assertTrue(rewritten.contains("## --- gameplay/profile (preserved if present) ---"));
        assertTrue(rewritten.contains("## --- HauntedMC enforced defaults (DO NOT CHANGE) ---"));
        assertTrue(rewritten.contains("custom-key=override"));
        assertTrue(rewritten.contains("difficulty=hard"));
        assertTrue(rewritten.contains("enable-code-of-conduct=true"));
        assertTrue(rewritten.contains("enable-status=false"));
        assertTrue(rewritten.contains("function-permission-level=1"));
        assertTrue(rewritten.contains("hide-online-players=true"));
        assertTrue(rewritten.contains("max-players=999"));
        assertTrue(rewritten.contains("max-tick-time=60000"));
        assertTrue(rewritten.contains("chat-spam-threshold-seconds=10"));
        assertTrue(rewritten.contains("command-spam-threshold-seconds=10"));
        assertTrue(rewritten.contains("management-server-allowed-origins="));
        assertTrue(rewritten.contains("management-server-enabled=false"));
        assertTrue(rewritten.contains("management-server-host=localhost"));
        assertTrue(rewritten.contains("management-server-port=0"));
        assertTrue(rewritten.contains("management-server-secret="));
        assertTrue(rewritten.contains("management-server-tls-enabled=true"));
        assertTrue(rewritten.contains("management-server-tls-keystore="));
        assertTrue(rewritten.contains("management-server-tls-keystore-password="));
        assertTrue(rewritten.contains("op-permission-level=0"));
        assertTrue(rewritten.contains("status-heartbeat-interval=0"));
    }

    @Test
    void createsFileWhenMissing() throws Exception {
        Path scenario = TestPaths.features(tmp, "sanitize", "server-properties-create");
        Path serverRoot = TestPaths.serverRoot(scenario);

        ServerPropertiesSanitizeTask task = new ServerPropertiesSanitizeTask();
        SanitizeResult result = task.run(new SanitizeContext(serverRoot, "1.21.1"));

        assertTrue(result.changed());
        assertTrue(Files.exists(serverRoot.resolve("server.properties")));
    }
}
