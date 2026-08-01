# CommandLogger

> Paper · Feature name `CommandLogger` · feature package `features.commandlogger` · disabled by default

CommandLogger observes player, local console and RCON command events and asynchronously stores verified command lines in MySQL. It records the backend name, canonical DataRegistry player ID when applicable, source class label, command text without one leading slash, and admission timestamp.

The implementation currently performs no command exclusion or argument redaction. Every existing command for which the source passes `Command#testPermissionSilent` is eligible, including commands containing passwords, tokens or sensitive arguments.

## Commands and permissions

CommandLogger registers no command and exposes no viewer/admin permission. It is passive auditing only.

A command is logged only when all listener checks pass:

1. command event reaches the `MONITOR` listener uncancelled;
2. normalized command text is non-blank;
3. Bukkit's command map can be obtained reflectively;
4. the first token resolves to a registered `Command`;
5. `cmd.testPermissionSilent(source)` returns true.

There is no bypass permission and no per-command allow/deny list.

## Complete configuration reference

File: `plugins/ServerFeatures/features/CommandLogger/config.yml`.

| Key | Default | Meaning |
|---|---:|---|
| `enabled` | `false` | Enables DataProvider/ORM initialization and the three command listeners. |

Global setting:

| Key | Use |
|---|---|
| `server_name` | Stored in every database row. No local fallback/validation is applied in the service. |

There are no configuration keys for exclusions, redaction, source types, retention, batching, retry, maximum length, storage mode or privacy controls. Those earlier documented options do not exist.

## Required integrations and initialization

Startup order:

1. initialize DataProvider for feature name;
2. register logical connection `ormConnection` as MySQL with access policy `player_data_rw`;
3. create `ORMContext` for `CommandExecutionEntity`;
4. throw `IllegalStateException` if context cannot be created;
5. construct `CommandLogService`, requiring DataRegistry;
6. register `CommandListener`.

DataRegistry is mandatory because player executions must map to canonical numeric player IDs. Absence produces `IllegalStateException("DataRegistry is required for CommandLogger.")`.

## Database schema

Table: `player_command_executions`

| Column | ORM mapping | Meaning |
|---|---|---|
| `id` | generated identity `Long`, primary key | Audit-row ID. |
| `server` | string length 100, non-null | Global backend/server name at admission. |
| `player_id` | nullable `Long` | Canonical DataRegistry player ID; null for console/RCON/non-player sources. |
| `source` | string length 150, non-null | Lowercase Java simple class name of the `CommandSender`, for players as well as non-players. |
| `command` | string length 400, non-null | Full submitted line with at most one leading slash removed. |
| `timestamp` | non-null `Long` | Epoch milliseconds captured before identity/database completion. |

Despite the entity comment saying `source` should only be set for non-player sources, the service sets it for every source. Typical player value is based on the concrete sender class simple name and may vary across Paper versions/wrappers rather than being a stable enum such as `player`.

Commands longer than 400 characters are not truncated/redacted and may fail database persistence.

## Event coverage and ordering

All handlers use:

```java
priority = EventPriority.MONITOR
ignoreCancelled = true
```

### `PlayerCommandPreprocessEvent`

Captures player-issued slash commands. Removes one leading `/`, then performs command-map and permission verification.

Because this runs at `MONITOR`:

- commands cancelled by lower-priority plugins are not logged;
- command/message modifications made before `MONITOR` are logged in their final event form;
- plugins should conventionally not modify cancellation after monitor, but non-standard listeners can still affect whether execution actually happens;
- the event indicates preprocessing, not guaranteed successful command completion. A command can pass permission/existence checks and later fail during execution while still being logged.

### `ServerCommandEvent`

Captures local console and other server-command senders represented by this event. The command text normally has no slash; one is removed if present.

### `RemoteServerCommandEvent`

Captures RCON commands. Depending on Paper event inheritance/dispatch, verify that RCON does not also appear through `ServerCommandEvent`; the feature itself has no duplicate-correlation guard.

The feature does not listen to:

- proxy/Velocity commands;
- commands invoked directly through `CommandMap#dispatch` when no corresponding event fires;
- Brigadier/internal callbacks bypassing Bukkit preprocess/server events;
- plugin actions that call command handlers directly.

## Command-map verification

`getCommandMap()` reflectively invokes a public `getCommandMap` method on the runtime server implementation.

- Any reflection error is swallowed and returns null.
- When null, logging silently stops for that event; no warning/metric is emitted.
- Alias is the first whitespace-delimited token after leading whitespace removal.
- Namespaced aliases are accepted when `CommandMap#getCommand(alias)` resolves them.
- Leading spaces are ignored for alias resolution, but the stored `fullCommand` is the original post-slash string and can retain leading spaces.
- Only one leading slash is stripped; `//command` becomes `/command` and may fail map lookup depending on alias.

`testPermissionSilent(source)` verifies only Bukkit command permission. It does not ensure argument validity, sender type, cooldown, downstream policy or successful execution.

## Persistence pipeline

`logServerCommand(source, fullCommand)` captures immediately:

- `timestamp = System.currentTimeMillis()`;
- global `server_name`;
- `sourceLabel = source.getClass().getSimpleName().toLowerCase(Locale.ROOT)`.

### Player source

1. call `PlayerIdentityResolver.whenReady(uuid)`;
2. on failure, warn `DataRegistry identity unavailable for command log` and discard;
3. empty identity is silently discarded;
4. resolved identity supplies canonical `playerId`;
5. schedule asynchronous ORM persistence.

### Non-player source

Schedules persistence immediately with `playerId = null`.

### Async transaction

The feature task manager schedules an async task that calls:

```java
ormContext.runInTransaction(session -> session.persist(entity))
```

Submission failure is caught and logged. There is no explicit completion handler, retry, durable queue, batching, backpressure, metric or dead-letter record.

The timestamp/source/server/command are captured before asynchronous delay, so rows reflect event admission rather than commit time.

## Sensitive-data and redaction warning

The feature stores the complete command line exactly as received after slash normalization. It does not redact:

- login/authentication passwords;
- two-factor/recovery codes;
- API tokens;
- webhook URLs;
- database credentials;
- private-message contents in command-based messaging systems;
- moderation reasons containing personal data;
- arbitrary plugin command secrets.

Before enabling, inventory all installed commands and either add a redaction/exclusion layer or ensure no sensitive command arguments can be entered. Database access and retention must be restricted accordingly.

Redaction should occur synchronously before the string is submitted to asynchronous persistence and should be based on normalized command root/argument policy, not simple global string replacement.

## Interaction with command cancellation and execution

A logged row means:

> An uncancelled command event reached CommandLogger at `MONITOR`, resolved to a registered command, and the source passed that command's Bukkit permission test.

It does **not** prove:

- command executor ran;
- arguments were valid;
- command succeeded;
- state changed;
- downstream permission/policy checks passed;
- output was delivered.

For outcome auditing, commands/services must emit explicit success/failure audit events after execution.

## Threading

Bukkit command events run on the main server thread under normal operation. Reflection, map lookup, alias parsing and permission testing occur synchronously there. Identity resolution/database write are asynchronous.

No player/Bukkit object is retained into the database task; player UUID and source label are resolved/captured first. The identity completion callback passes primitive/string data to persistence.

## Persistence, messaging and API summary

- MySQL logical connection: `ormConnection`.
- Access policy: `player_data_rw`.
- ORM entity/table: `CommandExecutionEntity` / `player_command_executions`.
- Identity: DataRegistry canonical player ID.
- Redis/plugin messaging: none.
- PlaceholderAPI: none.
- public API/queries/admin UI: none.

This backend table is also conceptually compatible with proxy command logging, but source/server conventions must be coordinated explicitly across repos.

## Lifecycle and shutdown

`disable()` is empty. Framework lifecycle cleanup unregisters listeners and releases feature-scoped data/task resources.

Correct shared shutdown semantics are critical:

- new captures should stop once listener scope is retired;
- player identity callbacks already admitted may complete later;
- scheduled ORM transactions should reach a terminal state before connection teardown where DataProvider guarantees that;
- no local queue exists to flush.

A database/identity outage loses audit entries; the feature does not block command execution to guarantee logging.

## Developer source map

- Integration/lifecycle: `features/commandlogger/CommandLogger.java`
- Event normalization/verification: `features/commandlogger/listener/CommandListener.java`
- Identity and persistence: `features/commandlogger/service/CommandLogService.java`
- Entity schema: `features/commandlogger/entity/CommandExecutionEntity.java`
- Service tests: `src/test/.../features/commandlogger/service/CommandLogServiceTest.java`
- Metadata: `features/commandlogger/meta/Meta.java`

## Operational verification

1. Verify startup fails clearly without DataRegistry or authorised MySQL `player_data_rw` ORM context.
2. Execute player, console and RCON commands and inspect source/player ID/server/timestamp fields.
3. Test aliases, namespaced commands, leading spaces/slashes and unknown commands.
4. Test allowed/denied/cancelled/invalid-argument/failing commands and document what is logged.
5. Verify DataRegistry resolves players to canonical IDs across reconnect/name change.
6. Execute >400-character and sensitive commands in a non-production database to validate schema/redaction policy.
7. Test command-map reflection failure on target Paper version and ensure monitoring detects silent loss.
8. Test database/identity outage and server shutdown during admitted writes.
9. Confirm direct/plugin/proxy command paths outside the three events are not assumed covered.
10. Establish external retention/access auditing for the table.

## Troubleshooting

- **Nothing is logged:** check feature startup, command-map reflection on the Paper version, event cancellation, command existence/permission, DataRegistry and ORM logs.
- **Denied command is absent:** expected; `testPermissionSilent` filters it.
- **Failed command is present:** expected; logger records pre-execution verification, not outcome.
- **Player row is missing but console works:** inspect DataRegistry identity readiness/access.
- **Source values look implementation-specific:** they are lowercase Java sender class names, not stable enums.
- **Secrets appear in database:** no redaction exists; disable and implement policy before production use.
- **Proxy commands are absent:** this is Paper-side only; ProxyFeatures has its own command logger.
- **Rows disappear during shutdown/outage:** logging is best-effort asynchronous with no durable queue/retry.
