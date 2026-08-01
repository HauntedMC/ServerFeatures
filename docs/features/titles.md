# Titles

> Paper · Feature ID `titles` · disabled by default · delayed join presentation

Titles sends one localized Adventure title/subtitle sequence after every accepted `PlayerJoinEvent`. It has no command, permission, eligibility condition, database or network integration.

## Configuration

| Key | Default | Unit / meaning |
|---|---:|---|
| `enabled` | `false` | Enables the join listener. |
| `fade-in` | `10` | Ticks converted to `50 × value` milliseconds. |
| `stay` | `30` | Ticks converted to milliseconds. |
| `fade-out` | `30` | Ticks converted to milliseconds. |
| `delay` | `15` | Server ticks after join before sending. |

All four values are directly cast to `int` during `TitleHandler` construction and captured for the feature lifetime. Wrong types fail initialization. Negative timing values are passed to `Duration.ofMillis` and can throw; delay validity depends on the task manager/scheduler.

Changing these settings requires feature reconstruction before the handler observes them.

## Messages

| Key | Default / variables |
|---|---|
| `titles.join_title` | HauntedMC title component. |
| `titles.join_subtitle` | `Je bent nu in {server}`; `{server}` comes from global `server_name`. |

Messages are built immediately when `sendJoinTitle` is called from the join event, before the configured delay. Audience language/placeholders and `server_name` are therefore snapshotted at join handling time rather than at display time.

`server_name` is directly cast to `String`; missing/wrong-type global config can fail title construction.

## Event ordering

`PlayerJoinEvent` is handled at `HIGH` with `ignoreCancelled=true`.

The listener calls `sendJoinTitle`, which:

1. renders title for the joining player;
2. reads global server name;
3. renders subtitle with `{server}`;
4. constructs `Title.Times`;
5. schedules one delayed task;
6. sends title parts in this order:
   - `TIMES`;
   - `SUBTITLE`;
   - `TITLE`.

No event is cancelled or modified.

## Delayed-task lifecycle

The callback captures the original `Player`, title, subtitle and times. It does not re-check:

- whether the player is still online;
- whether the player changed world/server state;
- whether another join/session generation replaced the connection;
- whether the feature is still enabled.

Task cleanup relies on the lifecycle task manager. `disable()` itself is empty and does not clear already visible titles or explicitly cancel pending callbacks.

A rapid disconnect/reconnect can leave an old delayed callback targeting the same Bukkit player object/session semantics unless lifecycle cancellation prevents it.

## Commands, permissions and APIs

None. Every joined player receives the presentation. There is no first-join-only mode, vanished-player condition, world filter, permission bypass, player toggle or public service registration.

## Placeholder and integration behavior

The localization pipeline may process shared PlaceholderAPI values inside the two messages, but Titles registers no expansion.

Other title-producing plugins/features can overwrite this title immediately before, during or after its display. Titles has no queue, priority coordinator or ownership API.

## Threading and performance

Join handling and delayed title sends use the normal Bukkit task path. Work per join is two localization builds and one delayed task. There is no repeating task or persistent state.

## Important implementation boundaries

- Timings are ticks despite Adventure using durations internally.
- Values are captured at handler creation.
- Components are rendered before the delay.
- No online/session/generation check occurs at callback time.
- Disable does not explicitly clear titles.
- Every join is eligible.
- No command/permission/toggle exists.
- Other title sources can replace the presentation.

## Verification checklist

1. Verify exact 10/30/30 tick timing and 15-tick delay.
2. Change language/placeholders between join and display to confirm pre-render behavior.
3. Disconnect/reconnect before the delay and inspect stale callback behavior.
4. Disable/reload during pending delivery and validate lifecycle-manager cancellation.
5. Test zero, negative, very large and wrong-type timing values in a disposable server.
6. Combine with Restart, Parcour and other title senders to observe overwrite ordering.
7. Verify `{server}` for missing/changed global `server_name`.

## Source map

- Defaults/messages/lifecycle: `features/titles/Titles.java`
- Component/timing/task construction: `features/titles/internal/TitleHandler.java`
- Join priority: `features/titles/listener/PlayerLoginListener.java`
