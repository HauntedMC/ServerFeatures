package nl.hauntedmc.serverfeatures.features.backup.internal;

import nl.hauntedmc.serverfeatures.api.util.text.TextPatterns;
import nl.hauntedmc.serverfeatures.features.backup.Backup;
import nl.hauntedmc.serverfeatures.features.backup.internal.util.ServerRootResolver;
import nl.hauntedmc.serverfeatures.features.backup.internal.util.ZipUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

public class BackupService {

    private final Backup feature;

    public BackupService(Backup feature) {
        this.feature = feature;
    }

    public void runStartupBackup() {
        String folderName = asString(feature.getConfigHandler().get("backup_folder_name"), "backups");
        String zipPrefix = asString(feature.getConfigHandler().get("zip_name_prefix"), "backup_");
        int level = asInt(feature.getConfigHandler().get("compression_level"), 6);

        int dailyDays = asInt(feature.getConfigHandler().get("retention.daily_days"), 7);
        int keepMonthly = asInt(feature.getConfigHandler().get("retention.keep_monthly"), 1);
        int monthlyThresh = asInt(feature.getConfigHandler().get("retention.monthly_threshold_days"), 30);
        int keepQuarter = asInt(feature.getConfigHandler().get("retention.keep_quarterly"), 1);
        int quarterThresh = asInt(feature.getConfigHandler().get("retention.quarterly_threshold_days"), 90);

        List<String> includes = readIncludes();

        // Resolve server root & backup dir
        File serverRoot = ServerRootResolver.resolve(feature.getPlugin(), feature.getLogger());
        Path root = serverRoot.toPath();
        Path backupsDir = root.resolve(folderName).normalize();

        // Ensure backups dir exists
        try {
            Files.createDirectories(backupsDir);
        } catch (IOException e) {
            feature.getLogger().warning("Cannot create backups directory: " + backupsDir + " :: " + e.getMessage());
            return;
        }

        // Calendar day (no multiple per day)
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        String todayPrefix = zipPrefix + TextPatterns.DATE_FMT.format(today);
        boolean alreadyExists = hasBackupWithPrefix(backupsDir, todayPrefix);

        if (alreadyExists) {
            feature.getLogger().info("A backup for today already exists (prefix: " + todayPrefix + "). Skipping creation.");
        } else {
            // Build target zip name
            String zipName = zipPrefix + TextPatterns.TS_FMT.format(LocalDateTime.now(ZoneId.systemDefault())) + ".zip";
            Path zipPath = backupsDir.resolve(zipName);

            // Resolve & validate targets
            List<Path> targets = resolveTargets(root, includes);
            if (targets.isEmpty()) {
                feature.getLogger().warning("No valid targets to backup. Check 'include.paths' config.");
            } else {
                // Log plan
                feature.getLogger().info("Creating backup: " + zipPath.getFileName());
                feature.getLogger().info("Targets (" + targets.size() + "): " + targets.stream()
                        .map(root::relativize)
                        .map(Path::toString)
                        .collect(Collectors.joining(", ")));

                // Create zip
                long[] counters;
                try {
                    counters = ZipUtil.zipPaths(zipPath, root, targets, level);
                } catch (Throwable t) {
                    feature.getLogger().warning("Backup failed: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
                    counters = null;
                }

                // Log result
                if (counters != null) {
                    long files = counters[0];
                    long bytes = counters[1];
                    feature.getLogger().info("Backup created: " + zipPath.getFileName()
                            + " (" + files + " files, " + humanBytes(bytes) + ")");
                }
            }
        }

        // Retention enforcement
        try {
            enforceRetention(backupsDir, zipPrefix, ZoneId.systemDefault(), dailyDays, keepMonthly, monthlyThresh, keepQuarter, quarterThresh);
        } catch (Throwable t) {
            feature.getLogger().warning("Retention failed: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
        }
    }

    private void enforceRetention(Path backupsDir,
                                  String zipPrefix,
                                  ZoneId zone,
                                  int dailyDays,
                                  int keepMonthly,
                                  int monthlyThreshDays,
                                  int keepQuarterly,
                                  int quarterlyThreshDays) throws IOException {

        if (!Files.isDirectory(backupsDir)) return;

        LocalDate today = LocalDate.now(zone);
        LocalDate dailyCutoff = today.minusDays(Math.max(0, dailyDays - 1)); // inclusive range [cutoff..today]

        List<BackupEntry> all = Files.list(backupsDir)
                .filter(p -> p.getFileName().toString().startsWith(zipPrefix) && p.getFileName().toString().endsWith(".zip"))
                .map(p -> new BackupEntry(p, extractDate(p.getFileName().toString()).orElse(null)))
                .filter(be -> be.date() != null)
                .sorted(Comparator.comparing(BackupEntry::date).reversed())
                .toList();

        if (all.isEmpty()) {
            feature.getLogger().info("Retention: No backups found in " + backupsDir.getFileName());
            return;
        }

        List<Path> keep = new ArrayList<>();
        List<Path> delete = new ArrayList<>();

        // Keep all backups within daily window
        for (BackupEntry be : all) {
            if (!be.date().isBefore(dailyCutoff)) {
                keep.add(be.path());
            }
        }

        // Monthly bucket: date >= monthlyThreshDays old and < quarterlyThreshDays old
        List<BackupEntry> monthlyCandidates = all.stream()
                .filter(be -> daysOld(be.date(), today) >= monthlyThreshDays && daysOld(be.date(), today) < quarterlyThreshDays)
                .toList();

        keep.addAll(monthlyCandidates.stream()
                .limit(Math.max(0, keepMonthly))
                .map(BackupEntry::path)
                .toList());

        // Quarterly bucket: date >= quarterlyThreshDays old
        List<BackupEntry> quarterlyCandidates = all.stream()
                .filter(be -> daysOld(be.date(), today) >= quarterlyThreshDays)
                .toList();

        keep.addAll(quarterlyCandidates.stream()
                .limit(Math.max(0, keepQuarterly))
                .map(BackupEntry::path)
                .toList());

        // Everything else -> delete
        Set<Path> keepSet = new HashSet<>(keep);
        for (BackupEntry be : all) {
            if (!keepSet.contains(be.path())) {
                delete.add(be.path());
            }
        }

        int deleted = 0;
        List<String> fails = new ArrayList<>();
        for (Path p : delete) {
            try {
                Files.deleteIfExists(p);
                deleted++;
            } catch (IOException e) {
                fails.add(p.getFileName().toString());
            }
        }

        feature.getLogger().info("Retention: kept " + keepSet.size() + " backup(s), deleted " + deleted
                + (fails.isEmpty() ? "" : ("; failed: " + String.join(", ", fails))));
    }

    static int daysOld(LocalDate date, LocalDate today) {
        return (int) Duration.between(date.atStartOfDay(), today.atStartOfDay()).toDays();
    }

    static Optional<LocalDate> extractDate(String filename) {
        Matcher m = TextPatterns.DATE_IN_NAME.matcher(filename);
        if (m.matches()) {
            try {
                return Optional.of(LocalDate.parse(m.group(1), TextPatterns.DATE_FMT));
            } catch (Throwable ignored) {
            }
        }
        return Optional.empty();
    }

    /* --------------------- Helpers --------------------- */

    private boolean hasBackupWithPrefix(Path backupsDir, String prefix) {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(backupsDir, prefix + "*")) {
            for (Path ignored : ds) return true;
        } catch (IOException ignored) {
        }
        return false;
    }

    private List<String> readIncludes() {
        Object raw = feature.getConfigHandler().get("include.paths");
        if (raw instanceof List<?> list) {
            return list.stream().filter(Objects::nonNull).map(String::valueOf).toList();
        }
        if (raw instanceof String s) {
            String[] parts = s.split("[,;]");
            return Arrays.stream(parts).map(String::trim).filter(v -> !v.isEmpty()).toList();
        }
        return List.of();
    }

    private List<Path> resolveTargets(Path root, List<String> includes) {
        List<Path> out = new ArrayList<>();
        for (String inc : includes) {
            Path p = root.resolve(inc).normalize();
            if (Files.exists(p)) {
                out.add(p);
            } else {
                feature.getLogger().info("Skipping missing target: " + inc);
            }
        }
        return out;
    }

    static String humanBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format(Locale.ROOT, "%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    static String asString(Object v, String def) {
        return (v instanceof String s && !s.isBlank()) ? s : def;
    }

    static int asInt(Object v, int def) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    private record BackupEntry(Path path, LocalDate date) {
    }
}
