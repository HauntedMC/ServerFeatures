# ChatLog

> Paper · Feature name `ChatLog` · feature package `features.chatlog` · disabled by default

ChatLog persists accepted local Paper chat messages to MySQL and lets authorised players create immutable report snapshots for one or more known players over a configurable recent time window. Reports copy matching message rows into a separate table under a random report ID, return a clickable website URL, and optionally notify Discord.

The feature depends on DataProvider, the `player_data_rw` database connection policy, and DataRegistry's canonical player directory. It has no Redis contract and does not aggregate messages from other backend names unless those rows were written with the same configured `server_name` and queried from that server value.

## Commands and permissions

| Syntax | Permission | Sender | Behaviour |
|---|---|---|---|
| `/chatreport <player> [player...]` | `serverfeatures.feature.chatlog.use` | Player only | Resolves each unique requested name, checks whether that player has messages in the current backend/time window, copies rows for players with at least one match into a report ID, returns the report URL, and sends a Discord notification. |

There are no aliases or administration/inspection subcommands.

Tab completion returns current local online-player names matching the last argument. It does not check the command permission and does not suggest known offline players even though report creation supports them through DataRegistry.

Arguments are de-duplicated case-insensitively while preserving first occurrence and order. Each whitespace-separated argument is treated as one player name; there is no comma parsing.

## Complete configuration reference

File: `plugins/ServerFeatures/features/ChatLog/config.yml`.

| Key | Default | Meaning and edge cases |
|---|---:|---|
| `enabled` | `false` | Enables DataProvider/ORM initialization, chat capture and `/chatreport`. |
| `URL` | `https://hauntedmc.nl/chatlog/?report=` | Base URL concatenated directly with a generated 32-character report ID. Include all required path/query punctuation yourself. No URL encoding or validation is applied. |
| `reportTimeFrameMinutes` | `15` | Number of minutes before command execution included in counts/report copy. Direct cast to `int`. Zero creates an almost point-in-time interval; negative values produce a start timestamp in the future and normally no matches. |
| `discordWebhookURL` | `https://discordhook.url` | Discord webhook read when the report notification task runs. Empty/null logs a warning and skips. Replace the generated placeholder before production. |

Global setting used:

| Global key | Use |
|---|---|
| `server_name` | Stored on every chat row and used as an exact equality filter for report counts/copies and Discord metadata. |

There is no retention period, batch size, queue limit, redaction list, excluded world/channel, report-player limit, message-length config, or cross-server toggle.

## Required integrations and startup

Initialization order:

1. `initDataProvider(getFeatureName())`;
2. register connection name `ormConnection` as `DatabaseType.MYSQL` with access policy/owner `player_data_rw`;
3. create an `ORMContext` containing `ChatMessageEntity` and `ReportedChatMessageEntity`;
4. fail feature initialization with `IllegalStateException` if ORM creation is unavailable;
5. construct `ReportHandler` and require DataRegistry;
6. register the chat listener;
7. register `/chatreport`.

`ChatLogService` obtains `DataRegistryApi` from the plugin and throws `IllegalStateException("DataRegistry is required for ChatLog.")` when absent. DataProvider and DataRegistry are therefore hard requirements, not optional enhancements.

The data access identity must be authorised for `player_data_rw`. An access-policy exception at startup indicates DataProvider ownership/sharing configuration, not an event-listener problem.

## Database schema

### `player_chat_messages`

| Column | ORM type/constraints | Meaning |
|---|---|---|
| `id` | generated identity `Long`, primary key | Message-row identifier. |
| `server` | string length 100, non-null | Global `server_name` at capture time. |
| `player_id` | `Long`, non-null | Canonical DataRegistry player ID, not UUID. |
| `message` | string length 400, non-null | Plain serialized chat text. |
| `timestamp` | `Long`, non-null | Capture epoch milliseconds. |

### `player_reported_chat_messages`

| Column | ORM type/constraints | Meaning |
|---|---|---|
| `id` | generated identity `Long`, primary key | Copied report-row identifier. |
| `server` | string length 100, non-null | Source row server. |
| `player_id` | `Long`, non-null | Source canonical player ID. |
| `message` | string length 400, non-null | Source plain text. |
| `timestamp` | `Long`, non-null | Source timestamp. |
| `report_id` | non-null string | Random report correlation ID. No explicit column length annotation. |

A report is represented only by repeated copied rows sharing `report_id`; there is no report header entity containing creator, creation time, requested names or status. If no matching rows exist, no report record is created.

Messages longer than the mapped 400-character column may fail at persistence/database level depending on schema/database enforcement. The feature performs no truncation or explicit length validation.

## Chat capture event and ordering

```java
@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
public void onPlayerChat(AsyncChatEvent event)
```

At `HIGH`, after lower-priority filters:

1. serialize `event.message()` to plain text;
2. capture the `Player` reference;
3. schedule a feature-owned one-time server task;
4. that task invokes `reportHandler.logMessage(player, rawMessage)`.

Consequences:

- events cancelled by ChatFilter or another earlier listener are not logged;
- later listeners can still cancel or replace the message after ChatLog schedules capture, so a message cancelled at `HIGHEST` may still be logged;
- formatting, hover/click events and renderer prefix are not stored—only the event's plain message component at `HIGH`;
- direct system/plugin messages, private messages, proxy messages and `/broadcast chat` bypass this listener;
- the initial async chat callback avoids direct ORM work and hands off through the lifecycle task manager.

## Identity resolution and message-write pipeline

`addMessage(Player, rawMessage)` runs after the event handoff:

1. read global `server_name`;
2. capture `System.currentTimeMillis()`;
3. capture player UUID;
4. call `PlayerIdentityResolver.whenReady(uuid)`;
5. on resolver failure, log `DataRegistry identity unavailable for chat log` and discard;
6. when identity is empty, silently discard;
7. otherwise schedule asynchronous persistence;
8. run one ORM transaction and persist `ChatMessageEntity` with canonical `playerId`.

The timestamp is captured before identity readiness/database scheduling, so it represents log admission time rather than commit time.

`schedulePersist` catches failure to submit the async task and logs it. Exceptions occurring inside the transaction/task are handled by the task/ORM infrastructure; this service does not attach an explicit completion handler or retry.

There is no durable queue, backpressure, batching, retry policy, dead-letter storage or metric. A DataRegistry/database outage can lose messages.

An internal test-facing overload can persist synchronously with a Hibernate `Session` using an active cached UUID identity; production capture uses the asynchronous readiness path.

## Report command workflow

After permission/player/argument checks:

1. capture `currentTime`;
2. compute `reportStart = currentTime - reportTimeFrameMinutes * 60 * 1000L`;
3. read global `server_name`;
4. case-insensitively deduplicate requested names;
5. launch one asynchronous `countMessages` lookup per requested player;
6. wait for all lookups with `CompletableFuture.allOf`;
7. divide results into players with count > 0 and missing/count < 1;
8. schedule main-thread messages for every missing requested name;
9. if none have messages, also send `chatlog.errorNotSaved` and stop;
10. generate report ID `UUID.randomUUID().toString().replace("-", "")`;
11. concatenate configured base URL and report ID;
12. resolve player IDs again and asynchronously copy matching rows into report table;
13. on successful copy, schedule main-thread clickable URL delivery;
14. only when the creator is still online, send the URL and schedule Discord notification.

### Count queries

Each player name is resolved via DataRegistry `findByUsername`, including known offline identities. Unknown names return count zero.

For a resolved ID, an async ORM transaction executes:

```sql
SELECT COUNT(c)
FROM ChatMessageEntity c
WHERE c.server = :server
  AND c.playerId = :playerId
  AND c.timestamp BETWEEN :start AND :end
```

`BETWEEN` is inclusive at both endpoints.

A separate database transaction/query is used per requested player, so large argument lists can create many concurrent identity/database operations. There is no configured maximum.

### Report-copy query

Requested names with positive counts are resolved a second time. Resolved positive player IDs are deduplicated in insertion order. If none resolve, report creation fails with `IllegalArgumentException`.

One asynchronous transaction selects:

```sql
SELECT c
FROM ChatMessageEntity c
WHERE c.server = :server
  AND c.playerId IN :playerIds
  AND c.timestamp BETWEEN :start AND :end
```

No `ORDER BY` is specified. Returned/copy order therefore follows database/provider behaviour and must not be assumed chronological.

Every selected message becomes a newly persisted `ReportedChatMessageEntity` with the same server/player/message/timestamp and the generated report ID. There is no uniqueness constraint/idempotency guard; repeating report creation duplicates rows under a new ID.

The counts and copy occur in separate transactions and time. Rows can change/arrive between them. A player counted positive may ultimately contribute zero rows if data is externally modified; conversely only rows within the fixed captured end timestamp are eligible.

## Completion and lifecycle fencing

Async completions call `scheduleMain`, which submits a one-time lifecycle task and then checks `feature.getPlugin().isEnabled()` before running the user-facing callback.

- If lookup/copy fails, the creator receives `chatlog.errorNotSaved` when still online.
- Missing-player messages and report failure may both be sent when no requested player has messages.
- If the creator disconnects, report database creation can still complete, but the URL and Discord notification are skipped because the main callback returns when `player.isOnline()` is false.
- A successfully created report can therefore exist without creator feedback or Discord notification.
- Scheduling failure is logged as `Could not schedule chat report completion`.

The feature has no explicit cancellation token for already-admitted identity/ORM stages. DataProvider/feature lifecycle contracts determine whether in-flight work can finish during disable.

## User messages and variables

| Key | Variables | Use |
|---|---|---|
| `chatlog.help` | none | Missing arguments. |
| `chatlog.error` | `{name}` | Requested player has no message in the selected backend/window or is unknown. |
| `chatlog.url` | `{url}` | Successful clickable report link. Entire component receives `ClickEvent.openUrl(fullUrl)`. |
| `chatlog.errorNotSaved` | none | Count/create failure or no reportable players. |

There is no progress message while asynchronous lookups/copy run and no report ID shown separately from the URL.

## Discord notification

After URL delivery, `ReportHandler` schedules asynchronous `DiscordService.sendNotification`.

The yellow embed includes:

- fixed title `Nieuwe Chatreport` and description;
- embed URL and field containing the report link;
- creator's current player name;
- comma-joined requested player names that had positive counts;
- server name;
- current ISO-8601 timestamp;
- feature version in footer;
- fixed HauntedMC icon URLs.

Dynamic text is escaped with `JsonUtils.escapeJson`; transport uses `DiscordUtils.sendPayload`.

There is no retry, persisted webhook status, rate-limit handling, response validation or command feedback if Discord fails. Report database creation remains successful independently of Discord.

## Data retention and privacy

The feature defines no cleanup task or retention query for either table. Rows remain until an external job or administrator deletes them.

Stored data includes plain chat content, canonical player identity, backend name and timestamp; reported rows duplicate that content. Operators should define:

- lawful purpose/access policy;
- retention periods for normal and reported messages;
- database/web report authorization;
- log/webhook handling;
- deletion/export procedures;
- limits on report-link exposure.

The report ID is a random bearer identifier embedded in a URL. This feature does not authenticate the website or expire report rows/links.

## Threading model

- `AsyncChatEvent`: asynchronous entry, only serializes component and submits task.
- one-time task: begins identity resolution.
- DataRegistry completion: can complete asynchronously.
- database writes/count/copy: lifecycle-managed async tasks/ORM transactions.
- player messages and URL delivery: marshalled to one-time server tasks.
- Discord HTTP: lifecycle-managed async task.

This separation avoids blocking chat/main thread on database operations.

## Persistence and messaging summary

- MySQL connection: logical name `ormConnection`, access `player_data_rw`.
- ORM entities: `ChatMessageEntity`, `ReportedChatMessageEntity`.
- Identity: DataRegistry canonical numeric `playerId`.
- Redis/plugin messaging: none.
- PlaceholderAPI: none.
- Java API registration: none; `ReportHandler` is exposed only through the feature instance.

## Disable behaviour

`disable()` is empty. Scoped listeners, commands, tasks, ORM/data access are managed by the framework lifecycle. The feature itself does not flush a local queue because no local queue exists.

Correct DataProvider shutdown ordering is important: new work should be rejected after disable starts while admitted transactions are allowed to reach terminal success/failure. ChatLog relies on those shared guarantees.

## Developer source map

- Defaults/integration/lifecycle: `features/chatlog/ChatLog.java`
- Event capture: `features/chatlog/listener/ChatListener.java`
- Workflow facade: `features/chatlog/internal/ReportHandler.java`
- Persistence/identity/query logic: `features/chatlog/internal/services/ChatLogService.java`
- Discord webhook: `features/chatlog/internal/services/DiscordService.java`
- Main entity: `features/chatlog/entities/ChatMessageEntity.java`
- Report-copy entity: `features/chatlog/entities/ReportedChatMessageEntity.java`
- Command: `features/chatlog/command/ChatReportCommand.java`
- Service tests: `src/test/.../features/chatlog/internal/services/ChatLogServiceTest.java`

## Operational verification

1. Verify startup failure when DataRegistry or `MYSQL/player_data_rw` access is unavailable.
2. Send accepted, ChatFilter-blocked, later-cancelled and renderer-modified messages; confirm exact capture boundary.
3. Test plain/format-heavy messages and >400-character input against database schema.
4. Disconnect immediately after chat and verify identity-ready persistence still uses captured UUID/time.
5. Report online, known offline, unknown, duplicate-case and multiple players.
6. Test exact window boundaries and zero/negative/large timeframe values.
7. Verify reports are backend-scoped by `server_name`.
8. Inspect copied rows, report ID, lack of guaranteed ordering and duplicate report behaviour.
9. Disconnect the creator during count/copy and verify report may exist without link/Discord.
10. Test database/DataRegistry/task-scheduling failures and user/log feedback.
11. Test malformed base URL and webhook outage.
12. Load-test many names/messages and review query indexes for server/player/timestamp/report ID.
13. Verify external retention and web authorization because the plugin provides neither.

## Troubleshooting

- **No messages are stored:** check feature enablement, event cancellation before `HIGH`, DataRegistry identity readiness, `server_name`, ORM access and async-task logs.
- **Blocked ChatFilter messages are absent:** expected; ChatFilter cancels at `LOWEST` and ChatLog ignores cancelled events at `HIGH`.
- **A message cancelled by another late plugin is stored:** possible; capture is scheduled at `HIGH` before later cancellation.
- **Offline player cannot be reported:** ensure DataRegistry persisted the username identity and that rows use the same canonical player ID.
- **Report says no messages despite rows:** verify exact `server_name` and inclusive timestamp window.
- **Report website order is odd:** copy query has no `ORDER BY`; sort by timestamp in the web layer or add ordering.
- **Report exists but no Discord/link was shown:** creator may have disconnected after database creation.
- **Old data never disappears:** no retention implementation exists.
- **Database policy exception:** configure DataProvider ownership/shared access for `player_data_rw`; it is not resolved by changing chat events.
