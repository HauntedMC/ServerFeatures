# AFK

> Paper · Feature name `AFK` · feature package `features.afk` · disabled by default

The AFK feature maintains one in-memory activity state per online player. It supports manual AFK toggling, automatic timeout-based AFK entry, optional state-change broadcasts, long-idle kicks, an API for other features, and PlaceholderAPI output. Its activity engine deliberately distinguishes strong proof of player input from noisy movement packets and simple repeating actions.

## Operator summary

- State is local to the current Paper server and is not stored in a database or sent over Redis.
- Existing online players are bootstrapped when the feature is enabled; joining players receive a fresh activity timestamp and quitting players are removed immediately.
- A lifecycle-managed task runs every 20 ticks and checks automatic AFK entry and optional AFK kicks.
- Most Bukkit/Paper activity listeners run at `EventPriority.MONITOR` with `ignoreCancelled = true`, so cancelled gameplay actions do not count as activity.
- The feature is safe to disable or reload: its command, listener, task and API registrations are owned by the feature lifecycle, while `disable()` clears all tracked player state.

## Command and permission

| Command | Who | Permission | Behaviour |
|---|---|---|---|
| `/afk` | Player only | `serverfeatures.feature.afk.command.afk.toggle` | Toggles the sender's current AFK state. |

Console and command-block execution receives the configured `afk.usage` message. The command has no arguments and no tab completions.

Manual state changes use the same state-transition path as automatic changes: timestamps and anti-AFK samples are reset, the player receives the normal state message, and the optional broadcast is sent. Manually leaving AFK also clears an active AFK lock.

Commands named `afk`, including namespaced forms ending in `:afk`, are excluded from normal command activity. This prevents `/afk` itself from immediately undoing the state change that it requested.

## Complete configuration reference

Configuration file: `plugins/ServerFeatures/features/AFK/config.yml`.

| Key | Default | Meaning and constraints |
|---|---:|---|
| `enabled` | `false` | Enables the feature through the standard feature loader. |
| `afk_timeout_seconds` | `600` | Number of seconds since the last accepted activity before an active player is automatically marked AFK. Values `<= 0` disable the periodic timeout check entirely, which also means the kick branch is not evaluated. |
| `movement_distance_threshold` | `0.15` | Minimum horizontal distance, in blocks, for a move event to count as meaningful movement. The implementation compares squared X/Z distance against the squared threshold. |
| `rotation_threshold_degrees` | `10.0` | Minimum absolute yaw or pitch change that counts as meaningful rotation. Wrapped angular distance is used, so crossing `-180/180` is handled correctly. |
| `movement_vertical_epsilon` | `0.05` | A move with no qualifying horizontal movement but a Y delta above this value is treated as vertical-only movement for anti-AFK pattern sampling. Vertical-only movement does not independently refresh activity. |
| `broadcast_on_state_change` | `false` | Broadcasts configured enter/leave messages to all online players. The player's Adventure name component is supplied as `{name}`. |
| `kick_enabled` | `true` | Enables kicking players who remain AFK longer than `kick_timeout_seconds`. |
| `kick_timeout_seconds` | `3600` | Seconds measured from `afkSince`, not from the last activity timestamp. Values `<= 0` disable kicking while leaving automatic AFK detection enabled. |
| `combo_window_seconds` | `30` | Window in which meaningful movement/rotation and a weak auxiliary action may combine to prove that an AFK player is active. |
| `anti_afk.enabled` | `true` | Enables timing-pattern detection for weak actions, jumps and vertical-only movement. |
| `anti_afk.lock_seconds` | `60` | Duration of an AFK lock after a decision requests `LOCK_AFK`. While locked, a movement-plus-auxiliary combo cannot leave AFK. Current built-in rules expose the lock action for engine use; manual leaving AFK clears it. |
| `anti_afk.track_window_seconds` | `120` | Rolling retention window for anti-AFK timestamps. Older samples are removed whenever a new sample is added. |
| `anti_afk.min_samples` | `6` | Minimum number of intervals required before a pattern can be classified. Because intervals are calculated between timestamps, at least `min_samples + 1` timestamps must exist. |
| `anti_afk.mean_min_ms` | `800` | Lower bound for the mean interval between tracked actions. Patterns faster than this are ignored by the suspicious-pattern classifier. |
| `anti_afk.mean_max_ms` | `15000` | Upper bound for the mean interval between tracked actions. Patterns slower than this are ignored by the suspicious-pattern classifier. |
| `anti_afk.stddev_max_ms` | `120` | Maximum population standard deviation of tracked intervals. A mean inside the configured range and a standard deviation at or below this value marks the pattern suspicious. |

Configuration values are read when decisions are evaluated rather than copied into a long-lived immutable configuration object. Invalid types fall back to the hard-coded defaults. Numeric values are accepted through Java's `Number` interface and converted with `intValue()`/`doubleValue()`.

### Recommended relationships

- Keep `kick_timeout_seconds` greater than `afk_timeout_seconds`; otherwise a manually AFK player may still be kicked on the shorter AFK-duration timer.
- Keep `anti_afk.track_window_seconds` long enough to hold at least `anti_afk.min_samples + 1` actions at `anti_afk.mean_max_ms`, or slow repetitive patterns can never accumulate enough samples.
- Set `movement_distance_threshold` above small vehicle/plugin jitter but below ordinary player movement.

## Messages and variables

The feature provides these localization keys:

| Key | Variables | Used for |
|---|---|---|
| `afk.enabled_self` | none | Player enters AFK, manually or automatically. |
| `afk.disabled_self` | none | Player leaves AFK. |
| `afk.broadcast_enabled` | `{name}` | Optional network-local broadcast when a player enters AFK. |
| `afk.broadcast_disabled` | `{name}` | Optional network-local broadcast when a player leaves AFK. |
| `afk.kicked` | none | Adventure component supplied to `Player#kick`. |
| `afk.usage` | none | Non-player or invalid command usage. |
| `afk.placeholder.afk` | none | Formatted PlaceholderAPI output for an AFK player. |
| `afk.placeholder.not_afk` | none | Formatted PlaceholderAPI output for a non-AFK or unavailable player. |

Failures while sending state messages or broadcasts are contained so a localization problem cannot break the activity engine. A kick failure is likewise contained; the state entry is removed after the kick attempt.

## PlaceholderAPI

The expansion is registered only when the `PlaceholderAPI` plugin is present during feature initialization.

Identifier: `afk`

| Placeholder | Result |
|---|---|
| `%afk_boolean%` | `true` only when the requested player is currently online and marked AFK; otherwise `false`. |
| `%afk_binary%` | `1` for AFK and `0` otherwise. |
| `%afk_formatted%` | Serialized MiniMessage form of `afk.placeholder.afk` or `afk.placeholder.not_afk`. Falls back to literal `AFK` or `Active` if localization/serialization fails. |

Offline state is intentionally not retained. An `OfflinePlayer` supplied by PlaceholderAPI is resolved back to a currently online Bukkit player before state is read.

## Public API

`AfkAPI` is registered in ServerFeatures' feature API manager while the feature is active.

```java
boolean isAfk(UUID uuid)
```

The call is an in-memory lookup. It does not touch Bukkit player APIs, a database or Redis. Unknown and offline UUIDs return `false` because their state is absent.

## Activity event coverage

All accepted events are converted into an internal `AfkEvent` and evaluated by the ordered rule engine.

### Session lifecycle

| Event | Priority | Cancel handling | Effect |
|---|---|---|---|
| `PlayerJoinEvent` | `MONITOR` | n/a | Creates/refreshes state and sets `lastActivity` to the current time. |
| `PlayerQuitEvent` | `MONITOR` | n/a | Removes all state and anti-pattern samples. |

### Direct, strong activity

These actions are high-priority proof of real interaction. They clear a suspicious flag, leave AFK immediately, and refresh activity:

- `AsyncChatEvent` — the callback is rescheduled through the feature task manager before touching feature state, avoiding direct asynchronous state-transition work.
- `PlayerCommandPreprocessEvent` for every command except `/afk` and namespaced `*:afk`.
- `InventoryClickEvent`, `InventoryDragEvent`, `InventoryOpenEvent`, and `InventoryCloseEvent`.
- `BlockPlaceEvent`, `BlockBreakEvent`, `PlayerFishEvent`, attacks where the damager is a player, and `PlayerItemBreakEvent`.

Except for join/quit, handlers use `MONITOR` and `ignoreCancelled = true`.

### Weak auxiliary activity

The following events record `lastAux` and, when anti-AFK detection is enabled, add a timing sample:

- `PlayerInteractEvent`
- `PlayerInteractEntityEvent`
- `PlayerSwapHandItemsEvent`
- `PlayerDropItemEvent`
- `PlayerItemHeldEvent`
- `PlayerItemConsumeEvent`
- `PlayerToggleSneakEvent`
- `PlayerToggleSprintEvent`
- `PlayerAnimationEvent`
- Paper's `PlayerJumpEvent`

A weak action alone does not refresh a non-AFK player's activity timestamp. For an AFK player it only leaves AFK when meaningful movement/rotation occurred within `combo_window_seconds` and the state is not suspicious. This prevents a stationary repeating click/sneak/jump macro from continuously proving activity.

### Movement and teleportation

`PlayerMoveEvent` is decomposed into horizontal distance, vertical delta, yaw and pitch changes.

- Meaningful horizontal movement or rotation records `lastMove`.
- For a non-AFK, non-suspicious player, meaningful movement or rotation refreshes activity at low priority.
- For an AFK player, movement/rotation only leaves AFK when a weak auxiliary signal occurred within the combo window, the player is not suspicious, and the AFK lock has expired.
- Vertical-only movement above `movement_vertical_epsilon` is sampled by the anti-AFK classifier but does not count as normal movement.

`PlayerTeleportEvent` records `lastMove`. It refreshes activity only while the player is not AFK and not suspicious; a teleport imposed by another plugin therefore does not automatically release an AFK player.

## Decision ordering and state transitions

The engine evaluates every registered rule but only keeps decisions at the highest winning priority. Decisions of the same priority are merged into one action set.

Priority order is represented by the enum's declaration order and is used as follows:

- **High:** direct chat/command, inventory interaction, strong actions, and suspicious-pattern classification.
- **Medium:** entering/leaving AFK from composite evidence or timeout decisions.
- **Low:** ordinary activity timestamp refreshes from movement or teleportation.

This means a high-priority suspicious-pattern result can suppress a simultaneous lower-priority activity refresh. Applying a decision follows a deliberate order:

1. clear or set the suspicious flag;
2. apply AFK lock/unlock actions;
3. perform AFK leave, otherwise AFK enter;
4. refresh `lastActivity` only when the resulting state is not suspicious.

Entering or leaving AFK clears accumulated anti-AFK timestamps and combo signals. Leaving also resets `afkSince`, touches activity, and clears the lock. This prevents old movement/auxiliary samples from immediately triggering another transition.

## Automatic checks and timing

The feature schedules `tickCheck()` after 20 ticks and repeats every 20 ticks.

For each currently online player:

1. A missing state is created lazily.
2. A zero `lastActivity` is treated as the current time for that iteration.
3. Active players exceeding `afk_timeout_seconds` receive an `ENTER_AFK` decision.
4. AFK players are compared against `afkSince`; when kicking is enabled and the configured duration is positive, they are kicked after `kick_timeout_seconds`.

Timeout precision is therefore approximately one second, plus scheduler delay under server load.

## Anti-AFK classifier

The classifier stores timestamps, converts them into non-negative adjacent intervals, calculates the arithmetic mean, and then calculates population standard deviation (`sqrt(sum((x-mean)^2) / n)`). A pattern is suspicious only when all conditions are true:

1. at least `min_samples + 1` timestamps exist;
2. at least `min_samples` intervals exist;
3. mean interval is inside the inclusive configured minimum/maximum range;
4. population standard deviation is at or below `stddev_max_ms`.

When the suspicious flag is set, ordinary movement cannot refresh activity and movement-plus-weak-action combinations cannot leave AFK. A later high-confidence event such as chat, a non-AFK command, inventory use, block interaction, fishing, attacking, or an item breaking clears the flag and its historical samples.

## Persistence, database and messaging

AFK has no database entity, repository, Redis subscription, Redis publisher, or cross-server synchronization. State is intentionally server-local and ephemeral. Switching backend servers starts a new local activity state on join. Consumers needing a network-wide AFK concept must build an explicit cross-server contract rather than assuming this local API is global.

## Lifecycle and reload behaviour

Initialization order:

1. create `AfkService` and its engine;
2. bootstrap players already online;
3. create and register `AfkAPI`;
4. register `/afk`;
5. register `ActivityListener`;
6. schedule the one-second checker;
7. register the PlaceholderAPI expansion when available.

On disable, the feature clears its concurrent state map. Framework-owned commands, listeners, tasks and API registrations are retired by the feature lifecycle. No asynchronous database operation or messaging handle needs draining.

## Developer source map

- Feature entry point and defaults: `features/afk/AFK.java`
- Command: `features/afk/command/AfkCommand.java`
- Bukkit/Paper event bridge: `features/afk/listener/ActivityListener.java`
- Authoritative state/service: `features/afk/internal/AfkService.java`
- Public service: `features/afk/internal/AfkAPI.java`
- Placeholder expansion: `features/afk/internal/AfkPlaceholder.java`
- Decision engine: `features/afk/internal/engine/AfkEngine.java`
- Per-player state: `features/afk/internal/engine/player/AfkPlayerState.java`
- Rules: `features/afk/internal/engine/rules/`
- Engine and rule tests: `src/test/.../features/afk/internal/engine/`

## Operational verification

A complete in-game check should cover:

1. enable the feature while players are already online and confirm they are not immediately marked AFK;
2. verify `/afk` permission denial, player-only handling, toggle messages, broadcasts on/off, and PlaceholderAPI values;
3. wait beyond the automatic timeout and confirm AFK entry occurs within roughly one scheduler cycle;
4. confirm chat, a non-AFK command, inventory interaction and a strong gameplay action immediately leave AFK;
5. confirm movement alone and a weak action alone do not necessarily leave AFK, but both within the combo window do;
6. confirm cancelled interactions do not count;
7. confirm plugin-driven teleports do not release an already-AFK player;
8. reproduce a highly regular weak-action/vertical pattern and verify it stops ordinary activity refresh until a strong action clears it;
9. verify kicks are measured from AFK entry and that `kick_timeout_seconds <= 0` disables them;
10. disable/re-enable the feature and confirm no previous state or old anti-AFK samples survive.

## Troubleshooting

- **Players never become AFK:** another plugin may create qualifying horizontal/rotation changes, chat/command activity, or strong events. Increase movement/rotation thresholds and inspect event-producing plugins.
- **Players cannot leave AFK by walking:** this is intentional; AFK release from movement requires a recent weak auxiliary action. Chat, inventory use and strong actions leave immediately.
- **Legitimate players become suspicious:** increase `anti_afk.stddev_max_ms`, increase `anti_afk.min_samples`, narrow/shift the mean interval range, or disable `anti_afk.enabled`.
- **AFK players are never kicked:** `afk_timeout_seconds <= 0` returns before the kick branch, `kick_enabled` may be false, or `kick_timeout_seconds` may be non-positive.
- **Placeholder always reports false:** PlaceholderAPI only reports currently online local state; verify the feature was initialized while PlaceholderAPI was available.
- **State differs between servers:** expected; AFK is not persisted or synchronized across the proxy network.
