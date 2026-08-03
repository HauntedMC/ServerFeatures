# Sanitize

> Paper · Feature ID `sanitize` · disabled by default · asynchronous one-shot startup policy engine

Sanitize executes a configured ordered set of file-system and configuration tasks once when the feature initializes. It is not a general-purpose command, live validator or recurring cleanup service. Most enabled tasks rewrite files under the detected Paper server root; two tasks recursively delete old cache/version paths, one deletes dated logs, and one only reports gamerule differences.

The defaults enable every task. Enabling this feature therefore gives ServerFeatures ownership over substantial parts of `server.properties`, `bukkit.yml`, `spigot.yml`, `config/paper-global.yml`, the vanilla JSON/default files, `cache/`, `versions/` and `logs/`.

## Commands, permissions and APIs

Sanitize registers:

- no command;
- no permission;
- no listener;
- no PlaceholderAPI expansion;
- no database or Redis integration;
- no public feature service.

Its only runtime surface is startup log output and the resulting file changes.

## Execution lifecycle and ordering

During `initialize()` the feature creates a `SanitizeService`, appends task instances in this fixed order when their flags are enabled, and schedules one asynchronous task through the feature task manager:

1. `CacheSanitizeTask`;
2. `VersionsSanitizeTask`;
3. `DefaultConfigsSanitizeTask`;
4. `ServerPropertiesSanitizeTask`;
5. `BukkitYmlSanitizeTask`;
6. `SpigotYmlSanitizeTask`;
7. `PaperGlobalYmlSanitizeTask`;
8. `GameRulesCheckTask`;
9. `LogSanitizeTask`.

The service resolves the server root and Minecraft version once, builds a shared `SanitizeContext`, then runs tasks sequentially in that async worker. Each task is wrapped independently:

- success records `changed` or `unchanged` and elapsed milliseconds;
- any thrown `Throwable` increments the error count and allows later tasks to continue;
- the service prints a final totals line.

The outer scheduled callback also catches unexpected service-level failures.

`disable()` only clears the feature's service reference. Task cancellation and executor lifecycle belong to the feature lifecycle manager; there is no cooperative interruption inside file traversal or rewriting.

## Feature configuration

| Key | Default | Task |
|---|---:|---|
| `enabled` | `false` | Enables the feature. |
| `clean_cache_on_startup` | `true` | Remove old `cache/mojang_*.jar` files. |
| `clean_versions_on_startup` | `true` | Remove version directories other than the detected current Minecraft version. |
| `enforce_default_configs_on_startup` | `true` | Replace/create vanilla access-control/default files with fixed contents. |
| `enforce_server_properties_on_startup` | `true` | Rebuild `server.properties` into managed groups and enforce fixed values. |
| `enforce_bukkit_yml_on_startup` | `true` | Parse and rewrite `bukkit.yml`, enforcing selected paths. |
| `enforce_spigot_yml_on_startup` | `true` | Parse and rewrite `spigot.yml`, enforcing selected sections/paths. |
| `enforce_paper_global_yml_on_startup` | `true` | Parse and rewrite `config/paper-global.yml`, enforcing a large hard-coded policy. |
| `check_gamerules_on_startup` | `true` | Log every loaded-world gamerule that differs from the Paper registry default. Never changes gamerules. |
| `clean_logs_on_startup` | `true` | Delete matching dated logs older than the retention threshold. |
| `log_retention_days` | `7` | Retention used by the log task; negative values clamp to zero. |

Flags are type-checked as booleans; wrong types fall back to the method's supplied default (`false` during task registration). `log_retention_days` accepts a number or parseable string and otherwise falls back to `7`.

All policy values below are hard-coded in Java, not exposed through feature configuration.

## Server-root and version resolution

Tasks operate against the root returned by `ServerRootResolver`, not the plugin data folder. The current Minecraft version comes from `VersionResolver` and is used to identify:

- the one `cache/mojang_<version>.jar` to keep;
- the one `versions/<version>/` directory to keep.

Operators must verify root/version detection after changing launch layout, working directory, wrapper, container mounts or Paper version formatting. A wrong root or version can cause deletion/rewrite in unintended locations.

## File-system deletion safety model

Recursive deletion uses `SafeFs.deleteRecursively`:

- null returns false;
- an absent target returns true;
- the path is normalized;
- `Files.walkFileTree` deletes files then directories;
- existence is initially tested with `NOFOLLOW_LINKS`;
- any throwable causes false rather than propagating.

The helper does **not** compare the normalized path against the resolved server root, refuse filesystem roots, enforce an allowlist itself or explicitly reject a target that is a symbolic link before walking. Safety depends on each task constructing the exact intended child path.

Failures are summarized by filename/path; swallowed exceptions do not include stack traces from `SafeFs`.

## Cache cleanup

Target: `<server root>/cache`

Expected file: `mojang_<detected Minecraft version>.jar`.

The task scans only direct `*.jar` children:

- keeps a case-insensitive exact match for the expected filename;
- selects every other filename beginning with `mojang_` for recursive deletion;
- deliberately leaves non-Mojang JARs untouched;
- does not traverse nested cache directories except while deleting a selected target.

A failed deletion is still reported as a changed result because intervention was attempted. The task does not verify JAR contents or modification time.

## Versions cleanup

Target: `<server root>/versions`

The task scans direct child directories and keeps only the directory whose filename exactly equals the detected Minecraft version. Every other directory is recursively deleted. Files directly under `versions/` are ignored.

This is name-based, not age-based. It removes all historical/custom version directories regardless of whether another process still references them.

## Default configuration replacement

When enabled, the task creates or replaces these root files with exact normalized contents:

| File | Enforced content |
|---|---|
| `banned-ips.json` | `[]` |
| `banned-players.json` | `[]` |
| `eula.txt` | `eula=true` |
| `ops.json` | `[]` |
| `permissions.yml` | empty file |
| `whitelist.json` | `[]` |

Non-empty values are written with one trailing newline. Existing contents are compared after normalization, then truncated and replaced when different.

Operational consequences:

- vanilla IP/player bans are erased at every feature startup;
- vanilla ops are erased;
- vanilla whitelist entries are erased;
- EULA acceptance is forced to true;
- any manual `permissions.yml` content is erased.

This task is not merely ensuring files exist.

## `server.properties` policy

The task loosely parses non-comment lines by the first `=` or `:`. Keys and values are trimmed; later duplicates win. Existing comments and original ordering are discarded.

Output is rebuilt into:

1. unknown/new/deprecated keys, preserved as parsed;
2. selected gameplay/profile keys, preserved only when present;
3. HauntedMC enforced defaults at the bottom.

### Preserved gameplay/profile keys

Values are not changed for:

- `simulation-distance`;
- `view-distance`;
- `spawn-monsters`;
- `allow-nether`;
- `enable-code-of-conduct`;
- `difficulty`;
- `pvp`;
- `gamemode`;
- `hardcore`;
- `level-name`;
- `level-seed`;
- `level-type`;
- `max-world-size`.

If one of these is absent, Sanitize does not add a default for it.

### Enforced `server.properties` values

| Key | Value |
|---|---|
| `accepts-transfers` | `false` |
| `allow-flight` | `false` |
| `broadcast-console-to-ops` | `false` |
| `broadcast-rcon-to-ops` | `false` |
| `bug-report-link` | `https://hauntedmc.nl/support` |
| `chat-spam-threshold-seconds` | `10` |
| `command-spam-threshold-seconds` | `10` |
| `debug` | `false` |
| `enable-command-block` | `false` |
| `enable-jmx-monitoring` | `false` |
| `enable-query` | `false` |
| `enable-rcon` | `false` |
| `enable-status` | `true` |
| `enforce-secure-profile` | `true` |
| `enforce-whitelist` | `false` |
| `entity-broadcast-range-percentage` | `100` |
| `force-gamemode` | `true` |
| `function-permission-level` | `1` |
| `generate-structures` | `true` |
| `generator-settings` | `{}` |
| `hide-online-players` | `false` |
| `initial-disabled-packs` | empty |
| `initial-enabled-packs` | `vanilla` |
| `log-ips` | `true` |
| `management-server-allowed-origins` | empty |
| `management-server-enabled` | `false` |
| `management-server-host` | `localhost` |
| `management-server-port` | `0` |
| `management-server-secret` | empty |
| `management-server-tls-enabled` | `true` |
| `management-server-tls-keystore` | empty |
| `management-server-tls-keystore-password` | empty |
| `max-chained-neighbor-updates` | `1000000` |
| `max-players` | `999` |
| `max-tick-time` | `60000` |
| `motd` | empty |
| `network-compression-threshold` | `256` |
| `online-mode` | `false` |
| `op-permission-level` | `0` |
| `pause-when-empty-seconds` | `-1` |
| `player-idle-timeout` | `0` |
| `prevent-proxy-connections` | `false` |
| `query.port` | `25565` |
| `rate-limit` | `0` |
| `rcon.password` | empty |
| `rcon.port` | `25575` |
| `region-file-compression` | `deflate` |
| `require-resource-pack` | `false` |
| `resource-pack` | empty |
| `resource-pack-id` | empty |
| `resource-pack-prompt` | empty |
| `resource-pack-sha1` | empty |
| `server-ip` | empty |
| `server-port` | `25565` |
| `sync-chunk-writes` | `true` |
| `text-filtering-config` | empty |
| `text-filtering-version` | `0` |
| `spawn-protection` | `0` |
| `status-heartbeat-interval` | `0` |
| `use-native-transport` | `true` |
| `white-list` | `false` |

This policy assumes a proxied, offline-mode backend on port 25565 and disables vanilla whitelist enforcement. It can conflict with container-assigned ports, transfer support, command blocks, resource packs, server-list MOTD, RCON/query operations and custom compression settings.

## `bukkit.yml` policy

The task loads YAML through SnakeYAML into linked maps. Comments and original formatting are lost.

Enforced values:

| Path | Value |
|---|---|
| `settings.use-map-color-cache` | `true` |
| `settings.warn-on-overload` | `true` |
| `settings.permissions-file` | `permissions.yml` |
| `settings.update-folder` | `update` |
| `settings.plugin-profiling` | `false` |
| `settings.connection-throttle` | `4000` |
| `settings.query-plugins` | `false` |
| `settings.deprecated-verbose` | `default` |
| `settings.shutdown-message` | `De server is uitgezet.` |
| `settings.minimum-api` | `none` |
| `chunk-gc.period-in-ticks` | `600` |
| `aliases` | scalar `now-in-commands.yml` |

Other keys under `settings` are preserved. `spawn-limits`, `ticks-per` and `worlds` are preserved through map parsing/dumping. Unknown top-level keys are retained and also listed in an appended detected-keys comment section.

The generated header says fixed settings except `allow-end`; however the implementation simply enforces the listed paths and does not specially remove or modify `settings.allow-end`.

Controlled entries are annotated inline where `YamlSanitizeUtil` can match the emitted path.

## `spigot.yml` policy

SnakeYAML rewrites the entire file and removes existing comments/formatting. Unknown and non-controlled sections remain as parsed values.

### Messages

| Path | Value |
|---|---|
| `messages.whitelist` | Dutch maintenance message |
| `messages.unknown-command` | `Dit commando wordt niet herkend.` |
| `messages.server-full` | Dutch Premium/store message |
| `messages.outdated-client` | `Gebruik versie {0} om te kunnen spelen.` |
| `messages.outdated-server` | same |
| `messages.restart` | Dutch restart message |

### Commands

| Path | Value |
|---|---|
| `commands.tab-complete` | `1` |
| `commands.send-namespaced` | `false` |
| `commands.log` | `true` |
| `commands.spam-exclusions` | empty list |
| `commands.silent-commandblock-console` | `false` |
| `commands.replace-commands` | empty list |
| `commands.enable-spam-exclusions` | `false` |

### Settings

| Path | Value |
|---|---|
| `settings.bungeecord` | `false` |
| `settings.save-user-cache-on-stop-only` | `false` |
| `settings.sample-count` | `12` |
| `settings.player-shuffle` | `0` |
| `settings.user-cache-size` | `1000` |
| `settings.moved-wrongly-threshold` | `0.0625` |
| `settings.moved-too-quickly-multiplier` | `10.0` |
| `settings.timeout-time` | `60` |
| `settings.restart-on-crash` | `false` |
| `settings.restart-script` | `./restart` |
| `settings.netty-threads` | `4` |
| `settings.log-villager-deaths` | `true` |
| `settings.log-named-deaths` | `true` |
| `settings.debug` | `false` |

Attribute maxima are replaced with one-key maps:

- `settings.attribute.maxAbsorption.max = 2048.0`;
- `settings.attribute.maxHealth.max = 1024.0`;
- `settings.attribute.movementSpeed.max = 1024.0`.

Unlike Bukkit/Paper output, inline controlled comments are intentionally disabled for `spigot.yml`; the header enumerates all controlled paths.

## `paper-global.yml` policy

Target: `<server root>/config/paper-global.yml`. The directory is created when absent. SnakeYAML rewrites comments/formatting; controlled emitted lines receive inline comments where possible. Unknown top-level keys are preserved and listed in the generated header.

### Collisions and messages

- `collisions.enable-player-collisions=false`;
- `collisions.send-full-pos-for-hard-colliding-entities=true`;
- authentication-down uses the vanilla language component;
- Dutch connection-throttle/flying messages;
- `messages.no-permission=<red>Je mag dit commando niet uitvoeren.`;
- `messages.use-display-name-in-quit-message=false`.

### Unsupported/exploit settings

The task disables headless pistons, permanent block-break exploits, piston duplication, unsafe end-portal teleportation, skipped tripwire validation and skipped vanilla shield damage ticks. It enforces an empty oversized-item sanitizer exemption list, ZLIB compression, username validation and equipment updates on player actions.

### Watchdog and scoreboards

- early warning delay `10000` ms and interval `5000` ms;
- do not save empty scoreboard teams;
- do not track plugin scoreboards;
- Paper update checking enabled.

### Proxy settings

- `proxies.bungee-cord.online-mode=true`;
- `proxies.proxy-protocol=false`;
- `proxies.velocity.enabled=true`;
- `proxies.velocity.online-mode=true`;

`proxies.velocity.secret` is not controlled by Sanitize. Each server installation must configure its own secret manually and ensure that it exactly matches the Velocity proxy's forwarding secret. Sanitize preserves an existing value and does not add one when it is absent.

### Packet/spam limits, Spark and miscellaneous settings

- incoming packet threshold `300`;
- recipe increment/limit `1/20`;
- tab increment/limit `1/500`;
- all-packet limiter uses `KICK`, interval `7.0` and rate `500.0`;
- `minecraft:place_recipe` override uses `DROP`, interval `4.0` and rate `5.0`;
- packet-limit kick message uses the vanilla exceeded-rate language component;
- Spark enabled but not immediate;
- client interaction leniency and XP grouping set to `default`;
- permissions YAML loads before plugins;
- maximum joins per tick `5`;
- negative villager demand prevention disabled;
- full item-entity positions disabled;
- strict advancement dimension check disabled;
- alternative luck formula/custom spawner dimension type disabled.

Hardware/workload tuning remains installation-controlled: `player-auto-save`, `misc.chat-threads`, `misc.compression-level`, `misc.region-file-cache-size`, `chunk-loading-advanced`, `chunk-loading-basic` and `chunk-system` are preserved and are not added when absent.

### Commands and console

- player-as-vehicle for `/ride` disabled;
- player names are not suggested for null completions;
- `time.affects-all-worlds=false`;
- obsolete `commands.time-command-affects-all-worlds` is removed;
- console Brigadier completions/highlighting enabled;
- console does not automatically have all permissions.

### Item validation

- book author/title/display/lore limits `8192`;
- book page `16384`;
- page max `2560` and total multiplier `0.98`;
- selector resolution in books disabled.

### Logging, item obfuscation and block updates

- deobfuscate stack traces;
- the complete `anticheat.obfuscation.items` subtree is installation-controlled and preserved;
- chorus, mushroom, noteblock and tripwire updates remain enabled (`disable-* = false`).

### Chunk loading/system

Chunk loading and thread/system tuning are no longer controlled by Sanitize. Existing values are preserved and missing sections are left for Paper to default.

`_version`, `packet-limiter`, `time` and `update-checker` are recognized top-level Paper sections and are not reported as new/deprecated/other. `_version` itself is never controlled.

These keys are tightly coupled to the supported Paper version. Renamed/removed/type-changed upstream settings can be silently reintroduced as unknown YAML paths or rejected by Paper after restart.

## Gamerule audit

`GameRulesCheckTask` obtains every game rule from Paper's typed registry, sorted case-insensitively by key. For every currently loaded world it compares `World#getGameRuleValue` with `GameRule#getDefaultValue`.

Differences are logged individually. Unsupported rules for a world's feature set are skipped when access throws. The task always returns `unchanged`, even when differences exist, because it never mutates gamerules.

Because the whole sanitize suite runs on an async task, this task calls Bukkit world/gamerule APIs asynchronously. That is an important thread-safety boundary to review against the supported Paper API guarantees.

## Log retention

Target: direct files in `<server root>/logs`.

Matched filename pattern:

```text
YYYY-MM-DD-<number>.log
YYYY-MM-DD-<number>.log.gz
```

Case is ignored. `latest.log`, directories and nonmatching files are untouched.

Threshold is system-local `LocalDate.now().minusDays(retentionDays)`. A file is deleted only when the date encoded in its **filename** is strictly before the threshold. Modification time is ignored.

With retention `7`, a log exactly seven days old is retained until the next day. With retention `0`, every matching file before today's date is removed.

The summary denominator counts every matching candidate, including candidates newer than the threshold, so `Removed X/Y candidate files` does not mean Y files were eligible for deletion.

## Idempotence and comparison behavior

Text/YAML tasks compare normalized current and generated content and write only when different. Nevertheless:

- original comments are not preserved for YAML files;
- map ordering is replaced by linked-map/dumper ordering;
- duplicate properties collapse to the last value;
- unknown YAML keys are preserved as parsed data, not byte-for-byte;
- generated management headers/comments become canonical;
- an upstream formatter/type representation change can cause repeated rewrites.

`DefaultConfigsSanitizeTask` marks failed writes as a changed result. Cache/version tasks also treat failed deletion attempts as changed.

## Persistence, messaging and observability

Sanitize has no database. The filesystem itself is the persistent state.

Every task emits one summary with elapsed milliseconds. There is no dry-run mode, backup/rollback, audit file, metric, Discord report or per-path confirmation. Exceptions generally log only `getMessage()`.

Because the feature runs after the server process has already read startup configuration, changes to `server.properties`, Bukkit, Spigot and Paper settings usually take effect on the **next** server startup, not immediately in the current runtime.

## Critical operational boundaries

- Defaults enable destructive replacement/deletion tasks.
- Vanilla bans, ops and whitelist are forcibly emptied.
- Proxy/offline-mode/port policy is hard-coded.
- The Velocity forwarding secret and backend firewall remain installation responsibilities.
- YAML comments and formatting are discarded.
- There is no automatic backup or rollback.
- Version/cache deletion relies on correct server-root/version detection.
- Tasks run asynchronously, including Bukkit gamerule/world access.
- The policy is version-specific and can drift from future Paper schemas.
- Feature reload reruns the complete enabled task sequence.
- Disable does not undo completed writes/deletions.
- Manual changes to controlled paths are overwritten on every run.

## Verification checklist

1. Run first against a disposable copy of the complete server root and diff every changed/deleted path.
2. Verify resolved server root and detected Minecraft version in logs before enabling deletion tasks.
3. Place old/current/non-Mojang cache files and old/current version directories; confirm exact selection.
4. Populate bans, ops, whitelist and permissions files and verify that replacement is intentional.
5. Compare every preserved/enforced/unknown `server.properties` key after rewrite.
6. Add comments, unknown sections and custom values to Bukkit/Spigot/Paper YAML and inspect preservation versus formatting loss.
7. Configure a unique Velocity forwarding secret, confirm Sanitize preserves it, and verify that the backend accepts connections only from the proxy.
8. Validate generated files with the exact deployed Paper build before production restart.
9. Create gamerule differences in multiple worlds and confirm audit-only behavior.
10. Create matching/nonmatching logs around the retention boundary and confirm filename-date semantics.
11. Deny write/delete permissions to selected paths and inspect partial-success/error reporting.
12. Reload/disable during a long deletion pass and verify lifecycle-manager behavior in a controlled environment.

## Source map

- Task selection and defaults: `features/sanitize/Sanitize.java`
- Ordered execution/reporting: `features/sanitize/internal/SanitizeService.java`
- Shared context/result/task contracts: `features/sanitize/internal/task/`
- Individual policies: `features/sanitize/internal/task/impl/`
- Root/version detection: `features/sanitize/internal/util/ServerRootResolver.java`, `VersionResolver.java`
- Recursive deletion: `features/sanitize/internal/util/SafeFs.java`
- YAML conversion/comments/normalization: `features/sanitize/internal/util/YamlSanitizeUtil.java`
