package nl.hauntedmc.serverfeatures.features.sanitize.internal.task.impl;

import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeContext;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeResult;
import nl.hauntedmc.serverfeatures.util.TestFs;
import nl.hauntedmc.serverfeatures.util.TestPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LogSanitizeTaskTest {

    @TempDir
    Path tmp;

    private Path createLog(Path logsDir, LocalDate date, int part, boolean gz) {
        String fn = date + "-" + part + ".log" + (gz ? ".gz" : "");
        return TestFs.touch(logsDir.resolve(fn));
    }

    @Test
    void deletesFilesStrictlyOlderThanRetention() throws Exception {
        Path scenario = TestPaths.features(tmp, "sanitize", "logs-retention-delete-older");
        Path serverRoot = TestPaths.serverRoot(scenario);
        Path logs = TestPaths.dir(serverRoot, "logs");

        LocalDate today = LocalDate.now();
        int retention = 7;

        Path old1 = createLog(logs, today.minusDays(8), 1, false);
        Path old2 = createLog(logs, today.minusDays(30), 2, true);
        Path boundary = createLog(logs, today.minusDays(retention), 3, true);
        Path new1 = createLog(logs, today.minusDays(1), 4, false);

        TestFs.touch(logs.resolve("latest.log"));
        TestFs.touch(logs.resolve("readme.txt"));

        LogSanitizeTask task = new LogSanitizeTask(retention);
        SanitizeContext ctx = new SanitizeContext(serverRoot, "1.21.1");

        SanitizeResult result = task.run(ctx);

        assertTrue(result.changed());
        assertFalse(Files.exists(old1));
        assertFalse(Files.exists(old2));
        assertTrue(Files.exists(boundary));
        assertTrue(Files.exists(new1));
        assertTrue(Files.exists(logs.resolve("latest.log")));
        assertTrue(Files.exists(logs.resolve("readme.txt")));
    }

    @Test
    void keepsEverythingWhenNoCandidatesOrNoLogsDir() throws Exception {
        Path scenario = TestPaths.features(tmp, "sanitize", "logs-retention-no-dir");
        Path serverRoot = TestPaths.serverRoot(scenario);

        LogSanitizeTask task = new LogSanitizeTask(10);
        SanitizeContext ctx = new SanitizeContext(serverRoot, "1.21.1");

        SanitizeResult result = task.run(ctx);

        assertFalse(result.changed());
    }

    @Test
    void supportsGzAndPlainLogExtensions() throws Exception {
        Path scenario = TestPaths.features(tmp, "sanitize", "logs-retention-gz-plain");
        Path serverRoot = TestPaths.serverRoot(scenario);
        Path logs = TestPaths.dir(serverRoot, "logs");

        LocalDate today = LocalDate.now();
        int retention = 5;

        Path oldPlain = createLog(logs, today.minusDays(99), 1, false);
        Path oldGz    = createLog(logs, today.minusDays(99), 2, true);
        Path freshGz  = createLog(logs, today.minusDays(1), 3, true);

        LogSanitizeTask task = new LogSanitizeTask(retention);
        SanitizeContext ctx = new SanitizeContext(serverRoot, "1.21.1");

        SanitizeResult result = task.run(ctx);

        assertTrue(result.changed());
        assertFalse(Files.exists(oldPlain));
        assertFalse(Files.exists(oldGz));
        assertTrue(Files.exists(freshGz));
    }

    @Test
    void ignoresNonMatchingFilenames() throws Exception {
        Path scenario = TestPaths.features(tmp, "sanitize", "logs-retention-ignore-nonmatching");
        Path serverRoot = TestPaths.serverRoot(scenario);
        Path logs = TestPaths.dir(serverRoot, "logs");

        List<String> names = List.of(
                "2025-01-01.log",
                "2025-01-01-abc.log",
                "2025-13-01-1.log",
                "some-other-file.gz",
                "2025-01-01-1.LOG.GZ"
        );
        for (String n : names) {
            TestFs.touch(logs.resolve(n));
        }

        LogSanitizeTask task = new LogSanitizeTask(3650);
        SanitizeContext ctx = new SanitizeContext(serverRoot, "1.21.1");

        SanitizeResult result = task.run(ctx);

        assertFalse(result.changed());
        for (String n : names) {
            assertTrue(Files.exists(logs.resolve(n)));
        }
    }

    @Test
    void boundaryIsNotDeleted() throws Exception {
        Path scenario = TestPaths.features(tmp, "sanitize", "logs-retention-boundary");
        Path serverRoot = TestPaths.serverRoot(scenario);
        Path logs = TestPaths.dir(serverRoot, "logs");

        int retention = 10;
        LocalDate today = LocalDate.now();

        Path boundary = createLog(logs, today.minusDays(retention), 1, true);
        Path older = createLog(logs, today.minusDays(retention + 1), 2, true);

        LogSanitizeTask task = new LogSanitizeTask(retention);
        SanitizeContext ctx = new SanitizeContext(serverRoot, "1.21.1");

        SanitizeResult result = task.run(ctx);

        assertTrue(result.changed());
        assertTrue(Files.exists(boundary));
        assertFalse(Files.exists(older));
    }
}
