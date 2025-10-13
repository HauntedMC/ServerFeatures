package nl.hauntedmc.serverfeatures.features.sanitize.internal.task.impl;

import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeContext;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeResult;
import nl.hauntedmc.serverfeatures.features.sanitize.internal.task.SanitizeTask;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogSanitizeTask implements SanitizeTask {

    private static final Pattern LOG_FILE =
            Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})-\\d+\\.log(?:\\.gz)?$", Pattern.CASE_INSENSITIVE);

    private final int retentionDays;

    public LogSanitizeTask(int retentionDays) {
        this.retentionDays = Math.max(0, retentionDays);
    }

    @Override
    public String name() {
        return "Logs";
    }

    @Override
    public SanitizeResult run(SanitizeContext ctx) throws IOException {
        Path logsDir = ctx.serverRoot().resolve("logs").normalize();

        if (!Files.isDirectory(logsDir)) {
            return SanitizeResult.unchanged("Skipped — folder not found: " + logsDir);
        }

        LocalDate threshold = LocalDate.now(ZoneId.systemDefault()).minusDays(retentionDays);

        int candidates = 0;
        int deleted = 0;
        List<String> failed = new ArrayList<>();

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(logsDir)) {
            for (Path p : ds) {
                if (Files.isDirectory(p)) continue;

                String fn = p.getFileName().toString();
                if ("latest.log".equalsIgnoreCase(fn)) {
                    continue;
                }

                Matcher m = LOG_FILE.matcher(fn);
                if (!m.matches()) {
                    continue;
                }

                candidates++;
                LocalDate fileDate;
                try {
                    fileDate = LocalDate.parse(m.group(1), DateTimeFormatter.ISO_LOCAL_DATE);
                } catch (DateTimeParseException e) {
                    continue;
                }

                if (fileDate.isBefore(threshold)) {
                    try {
                        Files.deleteIfExists(p);
                        deleted++;
                    } catch (Throwable ex) {
                        failed.add(fn);
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Retention: ").append(retentionDays)
          .append(" day(s), threshold < ").append(threshold)
          .append("; Removed ").append(deleted).append("/").append(candidates).append(" candidate file(s)");
        if (!failed.isEmpty()) sb.append(" [failed: ").append(String.join(", ", failed)).append("]");

        boolean changed = deleted > 0 || !failed.isEmpty();
        return changed
                ? SanitizeResult.changed(sb.toString())
                : SanitizeResult.unchanged("No log files to purge. " + sb);
    }
}
