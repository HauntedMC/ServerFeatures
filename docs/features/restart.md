# Restart

> Paper · Feature ID `restart` · disabled by default · command aliases `/restart`, `/reboot`

Restart implements a complete backend restart lifecycle rather than invoking Paper shutdown immediately. Immediate, forced, scheduled and daily restarts all converge on one state machine that announces the restart, closes joins, optionally publishes proxy autoreconnect preparation, saves the server, removes players at a bounded rate, verifies the backend is empty, saves again and finally calls `Server#shutdown()`.

The Paper feature never selects a fallback server itself. Players are kicked from the backend; ProxyFeatures can preserve and later reconnect eligible sessions when the durable lifecycle messages are enabled.

## Lifecycle phases

`RestartService` has seven mutually exclusive phases:

| Phase | Meaning | Cancellable |
|---|---|---|
| `IDLE` | No active operation. Joins are accepted. | Not applicable. |
| `SCHEDULED` | A one-off future wall-clock time is being monitored. | Yes. |
| `COUNTDOWN` | Per-second countdown is active. | Yes. |
| `FINAL_DELAY` | The zero-second announcement has fired and `auto.wait_after_now_seconds` is elapsing. | Yes. |
| `PREPARING` | Joins are closed and autoreconnect PREPARE is being published/settled. | Yes; cancellation reopens joins and publishes CANCEL where possible. |
| `DRAINING` | Players are being kicked in staggered passes. | No; cancellation reports `TOO_LATE`. |
| `SHUTTING_DOWN` | The final save completed and Paper shutdown was invoked. | No. |

A monotonically increasing sequence token fences every scheduled callback. Cancelling, forcing, disabling or replacing an operation increments the token, so stale countdown/monitor/drain tasks return without mutating a newer lifecycle.

Two atomics add terminal fencing:

- `shutdownCommitted` becomes true when PREPARING begins;
- `shutdownStarted` allows the shutdown path to execute once.

## Commands and permissions

The feature explicitly takes over the vanilla `/restart` command and unregisters the vanilla command before registering its own `FeatureCommand`. `/reboot` is an alias.

| Syntax | Permission | Behaviour |
|---|---|---|
| `/restart` | `serverfeatures.feature.restart.command.restart` | Starts the normal configured countdown when the service is idle. |
| `/restart force` | base permission **or** `serverfeatures.feature.restart.command.restart.force` | Skips countdown and enters the final/preparation path immediately. Rejected after PREPARING has begun. |
| `/restart schedule <date/day> <time>` | base permission **or** `serverfeatures.feature.restart.command.restart.schedule` | Schedules one future restart in the configured time zone. |
| `/restart cancel` | base permission **or** `serverfeatures.feature.restart.command.restart.cancel` | Cancels SCHEDULED, COUNTDOWN, FINAL_DELAY or PREPARING. |
| `/restart status` | base permission **or** `serverfeatures.feature.restart.command.restart.status` | Reports the active phase, target time, countdown seconds or online-player count. |

The base permission alone grants every subcommand because `canUse` checks `base || granular`. Granular subcommand permissions can also be granted without the base permission. Tab completion exposes only subcommands the sender can use.

`force` is intentionally rejected in `PREPARING`, `DRAINING` and `SHUTTING_DOWN`; using it during SCHEDULED, COUNTDOWN or FINAL_DELAY invalidates the existing operation, publishes CANCEL for any prepared marker, and starts immediate preparation under a new token.

### Supported schedule syntax

The parser accepts:

- one ISO local date-time token, such as `2026-08-03T05:00`;
- `<date> <time>` or `<time> <date>`;
- `<day-of-week> <time>` or `<time> <day-of-week>`.

Dates may use `yyyy-MM-dd`, `dd-MM-yyyy`, `dd/MM/yyyy` or `dd.MM.yyyy`. Times may use a colon or dot and one- or two-digit hour, such as `5:00`, `05:00`, `5.00` or `05.00`.

Day aliases include English and Dutch full/short forms: `monday/mon/maandag/ma` through `sunday/sun/zondag/zo`. A weekday resolves to the next occurrence in the configured zone; when that weekday/time has already passed today, it resolves one week later.

The target must be strictly in the future and only one scheduled/active restart can exist.

## Configuration

### Countdown presentation

| Key | Default | Meaning |
|---|---:|---|
| `enabled` | `false` | Enables command takeover, listeners, scheduler and optional messaging. |
| `title_fade_in` | `20` ticks | Title fade-in duration; negatives clamp to zero. |
| `title_stay` | `100` ticks | Title stay duration; negatives clamp to zero. |
| `title_fade_out` | `20` ticks | Title fade-out duration; negatives clamp to zero. |
| `broadcast.use_chat` | `true` | Sends countdown and scheduled announcements in chat. |
| `broadcast.use_titles` | `true` | Sends countdown titles/subtitles. |
| `announce.schedule` | `[60, 30, 10, 5, 4, 3, 2, 1, 0]` | Countdown seconds at which presentation is sent. Values are parsed, deduplicated and ordered descending by the service. The first/highest value is the countdown length. |

The service still ticks once per second even when a second is absent from `announce.schedule`; the set only controls presentation. Include `0` to send the “restart now” title/chat. An invalid or empty effective schedule must be treated carefully because countdown initialization expects a first entry.

### Daily automatic restart

| Key | Default | Meaning |
|---|---:|---|
| `auto.enabled` | `true` | Creates the daily scheduler when the feature initializes. |
| `auto.time` | `05:00` | Strict `HH:mm` daily trigger in `schedule.time_zone`. Invalid values log a warning and fall back to `04:00`. |
| `auto.wait_after_now_seconds` | `5` | Delay between the zero-second announcement and PREPARING. Also used by commanded countdowns. Clamped to zero or greater. |

The daily scheduler computes the next wall-clock occurrence. When the trigger fires it schedules the next day **before** asking the service to start, so an overlap that causes today's automatic restart to be skipped does not disable future daily triggers.

### One-off schedule monitoring

| Key | Default | Meaning |
|---|---:|---|
| `schedule.time_zone` | `system` | Zone used for parsing, monitoring and display. `system` means `ZoneId.systemDefault()`; other values are parsed by `ZoneId`. Invalid values fall back through the service's zone reader. |
| `schedule.check_interval_seconds` | `5` | Poll interval while in SCHEDULED. Values `<=0` fall back to `5`. |
| `schedule.announce_hours_before` | `5` | During the final N hours, send at most one scheduled chat announcement per wall-clock hour. Zero disables these announcements. |

A scheduled operation changes to COUNTDOWN only when a monitor tick observes `now >= target`; the trigger can therefore be late by up to the check interval plus scheduler delay.

### Controlled player drain

| Key | Default | Meaning |
|---|---:|---|
| `drain.player_interval_millis` | `150` | Delay between player kicks. Values `<=0` fall back to `150`. |
| `drain.poll_interval_millis` | `100` | Delay before another drain pass when players remain. Values `<=0` fall back to `100`. |
| `drain.empty_grace_millis` | `300` | Grace after a queue pass before checking whether the backend is empty. Negative values clamp to zero. |
| `drain.max_wait_seconds` | `20` | Bounded wait before the final staggered kick pass. Values `<=0` fall back to `20`. |

The drain queue is a snapshot of online UUIDs sorted by UUID string. Before draining, the feature dispatches `save-all`. It then kicks one currently online queued player per interval. After a pass it waits the empty grace and rebuilds the queue from all players still online. If the deadline expires, it performs one last staggered pass and then proceeds to shutdown after at least 500 ms, even if Paper still reports players online. A warning records this fail-safe condition.

Immediately before shutdown the feature dispatches `save-all` again and calls `Server#shutdown()`. There is no configurable shell command, panel API, `restart` script or `spigot.yml` restart action in this implementation.

### Autoreconnect lifecycle messaging

| Key | Default | Meaning |
|---|---:|---|
| `autoreconnect.enabled` | `true` | Enables Redis lifecycle publication and the disk marker. Restart still works without it. |
| `autoreconnect.stream` | `server.restart.lifecycle` | Durable stream name. Blank values fall back to the default. |
| `autoreconnect.wait_after_ready_seconds` | `5` | Delay ProxyFeatures should wait after READY before reconnecting the first player. Stored in the message/marker. |
| `autoreconnect.player_interval_millis` | `250` | Suggested spacing between proxy reconnect attempts. |
| `autoreconnect.prepare_publish_timeout_millis` | `3000` | Maximum wait for PREPARE publication confirmation before continuing without guaranteed autoreconnect. Values `<=0` fall back to `3000`. |
| `autoreconnect.prepare_settle_millis` | `500` | Extra delay after confirmed PREPARE before player drain. Negative values clamp to zero. |
| `autoreconnect.session_ttl_seconds` | `600` | Expiry used in the restart marker and lifecycle payload. |
| `autoreconnect.ready_publish_attempts` | `12` | Maximum READY publication attempts after server load. |
| `autoreconnect.ready_retry_seconds` | `5` | Delay between READY attempts. |

Initialization requests a DataProvider Redis messaging provider named `restart-autoreconnect-redis` in namespace `hauntedmc`, then uses its durable access. Failure or absence logs a warning and disables only autoreconnect messaging; `RestartService` is created with a null publisher and continues directly from PREPARING to DRAINING.

The backend identity comes from global config `server_name`, defaults to `server`, is lower-cased, replaces characters outside `[a-z0-9_.:-]` with `_`, collapses underscores and truncates to 150 characters.

## Durable messaging contract

The Paper publisher emits `RestartLifecycleMessage` events to the configured durable stream. Every restart receives a random UUID-string `restartId`.

### PREPARE

PREPARE is emitted after joins close but before the first player is kicked. The payload includes:

- operation ID `restart.<restartId>.prepare`;
- restart ID and action `PREPARE`;
- normalized backend server name;
- creation and expiry epoch milliseconds;
- reconnect delay and player interval;
- the UUID strings of players online at preparation, sorted lexicographically.

Before publishing, the same identity/timing data is written to `plugins/ServerFeatures/restart/autoreconnect.properties`. Failure to write the marker makes PREPARE fail. Publication is awaited with the configured timeout. On timeout/failure the service logs that autoreconnect is not guaranteed and proceeds without the normal settle delay.

### CANCEL

Cancelling an operation after a marker exists emits `restart.<restartId>.cancel` with action `CANCEL` and no player list. Marker deletion is compare-by-restart-ID fenced: a late cancellation from an older sequence cannot delete a marker belonging to a newer restart.

Cancellation publication is best effort. Resetting the local restart state and reopening joins do not wait for Redis.

### READY

`RestartServerLoadListener` asks the publisher to emit READY only after Paper's server-load event, not merely plugin enable. The publisher reads the persisted marker, discards it when expired and publishes `restart.<restartId>.ready` with action `READY`. On success it compare-and-deletes the marker.

Failed READY publication retries up to `ready_publish_attempts` with `ready_retry_seconds` spacing while the marker remains unexpired and the publisher remains open. After the final failure it logs a severe error and leaves the marker on disk; a later restart/server load can retry until expiry.

The feature publishes lifecycle state only; player destination selection, holding-server eligibility, reconnect cancellation and actual reconnect execution belong to ProxyFeatures.

## Join fencing and event ordering

Joins remain open through SCHEDULED, COUNTDOWN and FINAL_DELAY. `beginPrepare` atomically changes phase to PREPARING and sets `joinsClosed=true` before publishing PREPARE or draining players.

`RestartJoinGuard` uses two layers:

| Event | Priority | Behaviour |
|---|---|---|
| `PlayerConnectionValidateLoginEvent` | `HIGHEST` | Sets the kick message when joins are closed, preventing admission as early as Paper's connection validation allows. |
| `PlayerJoinEvent` | `MONITOR` | Defensive fallback: immediately kicks a connection that passed validation before the gate closed. |

The join handler does not declare `ignoreCancelled`; its decision depends only on service state. The connection-level message has no player audience, while the fallback join kick formats for that player.

Cancelling in PREPARING invalidates all delayed work, reopens joins and attempts CANCEL. Once DRAINING starts, allowing cancellation could leave a partially evacuated backend and inconsistent proxy sessions, so the service refuses it.

## Countdown presentation and messages

Each announcement is rendered separately for every online player through localization.

For positive seconds, `{readable}` is supplied to:

- `restart.countdown.title`;
- `restart.countdown.subtitle`;
- `restart.countdown.chat`.

At zero the feature uses the separate `restart.countdown.now.*` messages without variables. Scheduled hourly chat uses `{datetime}`. Status messages use `{datetime}`, `{seconds}` or `{players}` according to phase.

`restart.kick` is the backend disconnect component. ProxyFeatures may replace the resulting player experience by moving or reconnecting the player, but Paper itself calls `Player#kick`.

## Cancellation semantics

`/restart cancel` maps phases to distinct results/messages:

- SCHEDULED → scheduled restart cancelled;
- COUNTDOWN → countdown cancelled;
- FINAL_DELAY → cancelled before preparation;
- PREPARING → preparation cancelled and joins reopen;
- DRAINING/SHUTTING_DOWN → too late;
- IDLE → nothing to cancel.

Cancellation clears scheduled time, countdown state, prepared marker reference, drain queue/deadline and both shutdown atomics. Existing Bukkit tasks are not individually cancelled; sequence-token checks make them inert when they next execute.

Feature disable cancels the daily scheduler token, calls service shutdown/reset and best-effort CANCEL, then closes the lifecycle publisher. It does not reverse a shutdown already started.

## Threading and performance

State transitions are synchronized on `RestartService`; externally visible phase/time fields are volatile. Redis futures complete asynchronously and re-enter the lifecycle through task-manager scheduling. Bukkit player enumeration, messages, kicks, command dispatch and shutdown occur from the scheduled server-task path.

The expensive operations are deliberately bounded:

- countdown work is one task per second;
- SCHEDULED work is one task per configured interval;
- player kicks are staggered;
- PREPARE publication has a timeout;
- drain has a deadline;
- READY retries have an attempt count and TTL.

## Important operational boundaries

- An external panel/container kill bypasses this state machine, PREPARE, saves, drain and CANCEL.
- The feature calls Paper shutdown, not a panel reboot endpoint. The surrounding process manager must actually restart the server process.
- Restart scheduling is in-memory. A one-off SCHEDULED operation is lost on plugin/server restart.
- Daily scheduling is recalculated on enable and does not persist “missed” runs.
- PREPARE failure does not abort shutdown; it only removes the autoreconnect guarantee.
- The player list in PREPARE is a snapshot before drain. Connections are fenced at PREPARING, but race handling still relies on the join guard and drain rechecks.
- After the bounded timeout, shutdown can proceed while Paper reports players online.
- CANCEL after PREPARE is best effort over Redis; ProxyFeatures must use operation/restart identity and expiry fencing.
- The disk marker contains no player list; it exists to preserve restart identity and timing until READY.
- `schedule.announce_hours_before` announcements are chat-only and occur at most once per observed clock hour.
- Granting the base command permission grants `force`, `schedule`, `cancel` and `status` as well.

## Verification checklist

1. Start `/restart`, inspect every configured countdown milestone and cancel during COUNTDOWN.
2. Cancel during FINAL_DELAY and PREPARING; verify joins reopen and no players are kicked.
3. Attempt cancellation after the first drain kick and verify it is refused.
4. Test `/restart force` from IDLE and while a countdown/schedule exists.
5. Exercise every accepted date/day/time format around same-day and next-week boundaries in the configured zone.
6. Verify the daily trigger reschedules tomorrow even when another operation causes today's trigger to be skipped.
7. Connect a player as PREPARING begins; verify connection validation or the join fallback rejects them.
8. Observe `save-all`, staggered kicks, empty grace, repeat passes, bounded timeout and final shutdown.
9. With Redis available, verify PREPARE operation ID, sorted UUID list, marker contents, CANCEL fencing and READY after full server load.
10. Stop Redis during PREPARE and READY to validate timeout/retry behaviour and that Paper still restarts.
11. Replace the marker with a newer restart ID before an old CANCEL/READY completion and verify compare-by-ID deletion preserves it.
12. Confirm the process supervisor restarts Paper after `Server#shutdown()`.

## Source map

- Defaults, initialization and Redis provider: `features/restart/Restart.java`
- State machine, countdown, scheduling and drain: `features/restart/internal/RestartService.java`
- Daily scheduler: `features/restart/internal/AutoRestartScheduler.java`
- Command tree and permissions: `features/restart/command/RestartCommand.java`
- Date/day parser: `features/restart/command/RestartScheduleParser.java`
- Vanilla command takeover: `features/restart/internal/CommandOverride.java`
- Early/late join fencing: `features/restart/listener/RestartJoinGuard.java`
- READY server-load hook: `features/restart/listener/RestartServerLoadListener.java`
- Durable publisher and marker lifecycle: `features/restart/messaging/RestartLifecyclePublisher.java`
- Disk format: `features/restart/messaging/RestartMarkerStore.java`
- Wire payload: `features/restart/messaging/RestartLifecycleMessage.java`
