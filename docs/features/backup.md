# Backup

> Paper · Feature name `Backup` · feature package `features.backup` · disabled by default

Backup creates one ZIP archive of configured server-root paths shortly after feature startup and then applies a date-based retention policy. It is intentionally a startup backup, not a repeating scheduler, command-driven backup, world snapshot system, database dump, or panel integration.

## Behaviour at a glance

- One asynchronous delayed task is scheduled five seconds after feature initialization.
- At most one archive is created for the current calendar day, determined by filename prefix.
- Configured files/directories are walked and written into a ZIP using paths relative to the resolved server root.
- Missing include targets are logged and skipped.
- Retention runs after creation is skipped, succeeds, or fails, provided the backup directory itself could be created.
- There is no command, permission, PlaceholderAPI expansion, database state, Redis messaging, or persisted task state.
- Live worlds are **not** included by the default configuration.
- The feature does not run `save-all`, pause writes, lock files, snapshot a filesystem, or coordinate database consistency.

## Scheduling and execution context

Initialization schedules:

```text
runStartupBackup() asynchronously after 5 seconds
```

The delay is expressed through the lifecycle task manager as `BukkitTime.seconds(5)`. The actual work runs off the main server thread, including directory traversal, ZIP compression, and retention deletion.

The task is feature-owned. If the feature is disabled before the delayed task begins, lifecycle cancellation should prevent it from running. Once I/O has started, the implementation has no explicit cancellation token or shutdown join; server operators should not disable the feature or stop the JVM while an archive is being written.

## Commands and permissions

Backup registers no command and checks no permissions. It cannot currently be started, inspected, cancelled, or retried in-game.

Operational control is limited to:

- enabling/disabling the feature;
- changing configuration before initialization;
- reviewing feature log output;
- managing the destination directory at the filesystem level.

## Complete configuration reference

File: `plugins/ServerFeatures/features/Backup/config.yml`.

| Key | Default | Meaning and edge cases |
|---|---|---|
| `enabled` | `false` | Enables construction of the service and scheduling of the one startup task. |
| `backup_folder_name` | `backups` | Path resolved against the detected server root. Blank/non-string values fall back to `backups`. The result is normalized but is not explicitly constrained to remain below the server root. Use a simple relative directory name. |
| `zip_name_prefix` | `backup_` | Prefix for duplicate-day detection, archive naming, retention matching, and date extraction. Blank/non-string values fall back to `backup_`. Changing it makes older archives invisible to the new retention run. |
| `compression_level` | `6` | ZIP deflate level. Valid explicit levels are `0..9`. Out-of-range values are replaced by `Deflater.DEFAULT_COMPRESSION`; numeric strings are accepted by the service parser. |
| `include.paths` | see below | Files/directories resolved against the server root. A YAML list is preferred. A single string is also accepted and split on commas or semicolons. Missing targets are skipped. |
| `retention.daily_days` | `7` | Keeps all recognized archives dated within the inclusive daily window `[today - (days - 1), today]`. Values below zero effectively behave as zero/one-day boundary logic because the cutoff uses `max(0, dailyDays - 1)`. |
| `retention.keep_monthly` | `1` | Number of newest candidates to keep in the monthly-age bucket. Negative values are clamped to zero at selection time. This is not one backup per calendar month. |
| `retention.monthly_threshold_days` | `30` | Minimum age in whole calendar days for the monthly bucket. Candidates must also be younger than `quarterly_threshold_days`. |
| `retention.keep_quarterly` | `1` | Number of newest candidates to keep at or beyond the quarterly threshold. Negative values are clamped to zero. This is not one backup per calendar quarter. |
| `retention.quarterly_threshold_days` | `90` | Minimum age in whole calendar days for the quarterly bucket. |

### Default include set

```yaml
include:
  paths:
    - plugins
    - config
    - bukkit.yml
    - commands.yml
    - server.properties
    - spigot.yml
```

These paths are resolved from the detected server root. Notably absent by default:

- world directories;
- `paper-global.yml`/`paper-world-defaults.yml` unless they are below the included `config` directory in the running layout;
- logs;
- server JARs;
- external database files/services;
- files outside the server root.

Add world directories explicitly only after considering consistency and archive size.

### Type parsing

- String settings require a non-blank Java `String`; otherwise the fallback is used.
- Integer settings accept any Java `Number` or a parseable numeric string.
- `include.paths` accepts a list or a comma/semicolon-delimited string; other types produce an empty include list.
- Configuration is read when `runStartupBackup()` begins, not retained in immutable fields at feature construction.

## Server-root resolution

`ServerRootResolver` tries, in order:

1. `plugin.getDataFolder()`;
2. its parent, assumed to be the `plugins` directory;
3. the parent of that directory, treated as the server root;
4. when no plugins parent is available, `Server#getWorldContainer()`;
5. on any failure, the absolute current working directory (`.`), with a warning.

In a standard layout such as `/server/plugins/ServerFeatures`, the resolved root is `/server`.

Operators using non-standard symlinks, mounted plugin directories, or wrapper launchers should verify the logged target list before relying on the archive.

## Backup-directory handling

The destination is:

```text
serverRoot.resolve(backup_folder_name).normalize()
```

`Files.createDirectories` creates the directory tree. If creation fails, the run logs a warning and returns before creation or retention.

There is no explicit `startsWith(serverRoot)` containment check. A value such as `../other-directory` can normalize outside the server root. This is powerful but should be treated as unsafe configuration: retention deletes matching ZIPs from the resolved directory.

The destination should not be included in `include.paths`; doing so risks walking an archive directory while writing the new archive and can produce recursive/unstable contents.

## Daily duplicate detection and naming

The feature uses the JVM system timezone.

### Duplicate-day prefix

For the current `LocalDate`:

```text
todayPrefix = zip_name_prefix + yyyy-MM-dd
```

A `DirectoryStream` checks for any entry matching `todayPrefix + "*"`. The check does not require `.zip`, valid timestamp syntax, or a regular file. Any matching file/directory suppresses archive creation for that day.

### Archive name

When no daily match exists:

```text
<zip_name_prefix><timestamp>.zip
```

The timestamp uses the shared `TextPatterns.TS_FMT`. Retention date extraction uses the shared date pattern embedded in the name. Keep custom prefixes compatible with that pattern and avoid manually renaming archives if they must remain retention-managed.

There is no temporary filename followed by atomic rename. The final `.zip` path is opened directly with `CREATE` and `TRUNCATE_EXISTING`. An interrupted write can therefore leave a partial file whose name may suppress a later same-day retry.

## Include resolution and archive paths

Each configured include is resolved as:

```text
serverRoot.resolve(include).normalize()
```

- Existing paths are accepted.
- Missing paths are logged as `Skipping missing target`.
- No explicit containment check rejects `..` paths outside the root.
- No deduplication removes overlapping includes. Including both `plugins` and `plugins/ServerFeatures` can attempt to write the same ZIP entry names twice and may fail with duplicate-entry errors.
- Symbolic-link traversal follows the behaviour of `Files.walk`/`Files` on the host filesystem; there is no explicit symlink policy or cycle guard in this feature.

ZIP entry names are normally relative to the server root and use `/` separators. If `rootBase.relativize(file)` throws because roots are incompatible, only the file name is used, which can cause collisions for external paths with identical names.

## ZIP creation details

`ZipUtil.zipPaths`:

1. validates the compression level, falling back when outside `0..9`;
2. creates the destination parent directories;
3. opens `ZipOutputStream` directly on the final path with create/truncate semantics;
4. walks every directory recursively;
5. skips directory entries and writes regular files encountered by the walk;
6. writes explicitly configured regular files;
7. copies each file through a buffered stream;
8. attempts to preserve each source file's last-modified time on the `ZipEntry`;
9. returns `[fileCount, uncompressedByteCount]`.

The archive contains file entries only; empty directories are not represented.

The reported byte count is the sum of source file sizes before compression, not final ZIP size. Logging formats it with binary 1024-based units (`KB`, `MB`, and so on).

### Failure behaviour

- An exception anywhere in the ZIP process logs one `Backup failed` warning.
- The partially written final ZIP is not deleted automatically.
- The run does not verify the finished archive by reopening it.
- Success is logged when `ZipUtil` returns; there is no explicit `fsync`, checksum, remote upload, or durability verification.
- Retention is still attempted after ZIP failure.

## Consistency model

Backup reads live files while the server and plugins may continue writing them.

There is no coordination with:

- Bukkit/Paper world save operations;
- plugin-specific flush hooks;
- SQLite/file databases;
- MySQL/MariaDB transaction snapshots;
- Redis;
- filesystem snapshots;
- container/panel backup locks.

Consequently, the ZIP is a best-effort file copy, not an application-consistent snapshot. This is usually acceptable for mostly static configuration/plugin JARs but is weaker for live world region files and mutable local databases.

For consistent world or database backups, use a dedicated snapshot/dump mechanism or extend this feature with explicit pre-backup save/quiesce and post-backup resume phases.

## Retention policy in detail

Retention scans only direct children of the configured backup directory that:

- start with the current `zip_name_prefix`;
- end with `.zip`;
- contain a date that matches `TextPatterns.DATE_IN_NAME` and parses with `TextPatterns.DATE_FMT`.

Unrecognized files are ignored and never deleted by this policy.

Recognized entries are sorted newest date first. Multiple archives with the same date retain filesystem/list ordering because the comparator only compares dates.

### Daily window

All backups whose parsed date is on or after:

```text
today.minusDays(max(0, daily_days - 1))
```

are kept.

Examples:

- `daily_days: 7` keeps today plus the previous six calendar dates.
- `daily_days: 1` keeps today.
- `daily_days: 0` also produces today's cutoff and therefore still keeps today.

### Monthly bucket

Candidates satisfy:

```text
daysOld >= monthly_threshold_days
daysOld < quarterly_threshold_days
```

The newest `keep_monthly` candidates are kept. This is a count-based age tier, not a monthly calendar sample. If three archives from consecutive days are in the bucket and `keep_monthly: 1`, only the newest one is kept.

### Quarterly bucket

Candidates satisfy:

```text
daysOld >= quarterly_threshold_days
```

The newest `keep_quarterly` candidates are kept. This is likewise a count-based oldest tier, not one per quarter.

### Deletion

Every recognized archive not in the union of the three keep sets is deleted with `Files.deleteIfExists`.

- Deletion failures are collected by filename and logged.
- The summary reports unique kept paths, successful delete attempts, and failures.
- No recycle bin/quarantine exists.
- Retention does not validate ZIP integrity before choosing what to keep or delete.

### Threshold pitfalls

- When `monthly_threshold_days >= quarterly_threshold_days`, the monthly bucket is empty.
- Negative thresholds make very recent/future-dated archives eligible for older buckets.
- Future-dated archive names have negative `daysOld` and are usually protected by the daily cutoff, depending on the date.
- Changing `zip_name_prefix` strands old files outside retention.

## Logging

Normal logs include:

- inability to create the backup directory;
- daily-skip decision and prefix;
- each missing include target;
- no-valid-target warning;
- archive filename and relative target list;
- success with file count and uncompressed size;
- ZIP failure reason;
- no-backups retention message;
- retention kept/deleted/failure summary;
- outer startup task failure.

There are no player-facing messages.

## Persistence, database and messaging

The feature does not register with DataProvider and does not itself back up remote databases. Including plugin configuration that contains database credentials does not back up the database contents.

It publishes no Redis messages and maintains no record of last successful backup beyond filenames in the destination directory.

## Lifecycle and shutdown

Initialization:

1. instantiate `BackupService`;
2. schedule an asynchronous one-shot task after five seconds.

Disable sets the service field to `null`. Lifecycle task ownership handles tasks that have not started, but there is no explicit wait/cancel protocol inside `BackupService` for a currently running archive. Do not perform feature reloads or abrupt server stops during backup I/O.

The service has no command/API exposure, so no other feature should retain it.

## Security considerations

Configuration controls both read paths and the directory in which retention deletes files. Treat write access to this config as equivalent to filesystem access under the server process account.

Risks to avoid:

- `..` paths escaping the intended server root;
- backing up secrets to a broadly readable directory;
- placing backups under a web-served path;
- including the backup destination in the archive;
- broad external paths with filename collisions;
- following symlinks into unexpectedly large/sensitive trees;
- retention prefixes matching unrelated ZIP files.

## Developer source map

- Defaults and startup scheduling: `features/backup/Backup.java`
- Backup/retention orchestration: `features/backup/internal/BackupService.java`
- ZIP writer: `features/backup/internal/util/ZipUtil.java`
- Server-root detection: `features/backup/internal/util/ServerRootResolver.java`
- Service tests: `src/test/.../features/backup/internal/BackupServiceTest.java`
- ZIP tests: `src/test/.../features/backup/internal/util/ZipUtilTest.java`
- Root-resolution tests: `src/test/.../features/backup/internal/util/ServerRootResolverTest.java`

## Operational verification

1. Test on a non-production copy with a dedicated backup directory.
2. Verify the detected server root and logged relative targets.
3. Confirm the default archive excludes worlds unless explicitly added.
4. Open the ZIP and verify expected relative paths and file readability.
5. Test each compression level boundary and an out-of-range value.
6. Confirm a second startup on the same calendar day skips creation.
7. Place a non-ZIP file matching today's prefix and observe that it also suppresses creation.
8. Simulate a missing include and verify it is skipped without failing other targets.
9. Test overlapping includes and remove any duplicate-entry configuration.
10. Seed dated test archives and verify daily/monthly/quarterly count-tier retention exactly.
11. Test a deletion-permission failure and inspect the logged failure list.
12. Interrupt a test backup and verify operational procedures detect/remove partial final ZIPs.
13. Verify disk space, ownership and off-host replication separately; this feature provides none of those checks.

## Troubleshooting

- **No archive appears:** check feature enablement, the five-second delay, backup-directory creation, include targets, and whether today's filename prefix already exists.
- **Archive contains no worlds:** expected with defaults; worlds are not included.
- **Archive is inconsistent/corrupt after a crash:** the feature writes live files directly to the final ZIP and has no atomic finalize or snapshot boundary.
- **Old archives are not deleted:** verify they match the current prefix, end in `.zip`, and contain a parseable date.
- **Too many recent archives are kept:** all files inside the daily date window are kept; monthly/quarterly counts do not reduce that set.
- **Expected one-per-month retention does not happen:** `keep_monthly` is a candidate count, not calendar bucketing.
- **Changing `message_interval`/schedule has no effect:** there is no schedule configuration; the implementation runs once, five seconds after startup.
- **Backup retries never occur after failure:** there is no retry or manual command. Remove a partial same-day prefix and reload/restart deliberately after fixing the cause.
- **Server shutdown hangs or produces a partial file:** avoid shutdown while the asynchronous ZIP is active; no in-progress drain protocol exists.
