# Bossbar

> Paper · Feature name `Bossbars` · feature package `features.bossbar` · disabled by default

Bossbar shows one Adventure boss bar to every player on the local Paper server and rotates that bar through a configured list of localized messages. Each player owns a separate `BossBar` instance so titles can be localized per audience. Optional auto-fade animation adjusts progress during a message's lifetime.

The feature is unconditional once enabled: there is no permission, audience rule, world filter, per-player toggle, database preference, or proxy synchronization.

## Behaviour at a glance

- Players already online receive a bar during feature initialization.
- Joining players receive the currently selected message; quitting players have their bar hidden and removed from the runtime map.
- When configured messages exist, the handler immediately applies the current entry and recursively schedules the next entry after that entry's duration.
- An empty message registry still gives players a fallback bar, but no rotation task starts.
- Auto-fade is global for the current message and writes progress to every active bar.
- Disabling the feature hides all tracked bars and clears the map.
- There are no commands, permissions, PAPI placeholders, database writes, or Redis messages.

## Commands and permissions

Bossbar registers no command and checks no permission. Every locally online player is eligible.

Other plugins can still hide an Adventure boss bar from a player, but this feature keeps the bar in its map and may update it again at the next message transition/fade step. There is no public API for suppressing an individual player.

## Feature configuration

File: `plugins/ServerFeatures/features/Bossbars/config.yml`.

| Key | Default | Meaning and edge cases |
|---|---:|---|
| `enabled` | `false` | Enables player listeners, initial bar creation and the message cycle. |
| `animation.steps_per_second` | `20` | Requested fade update density. Read each time an auto-fading message starts, clamped to at least `1`. The runtime read fallback is `5`, which differs from the generated default. |
| `animation.fade_delay` | `0` ticks | Delay before an auto-fade begins. Read for each fading message and clamped to at least `0`. When delay is greater than or equal to the message duration, no fade task is created. |

The feature config does not contain message definitions, audience conditions, update/placeholder intervals, bar flags, or rotation enablement. Message definitions live in `local/bossbars.yml`.

## `local/bossbars.yml` reference

The registry opens:

```text
plugins/ServerFeatures/local/bossbars.yml
```

Expected structure:

```yaml
messages:
  text1:
    message_key: text1
    duration: 100
    color: WHITE
    style: SOLID
    autoFade: false
    initialProgress: 1.0
```

Each direct child under `messages` becomes one `BossbarMessage`. Child iteration order defines rotation order.

### Per-message fields

| Field | Default | Behaviour |
|---|---:|---|
| `message_key` | child ID | Localization suffix. The final lookup is `bossbar.<message_key>`. |
| `duration` | `100` ticks | Time before the next message is selected. Passed directly through `BukkitTime.ticks`. Non-positive values are not explicitly rejected and may cause immediate/invalid scheduling depending on the task manager. |
| `color` | `WHITE` | Parsed as Bukkit `BarColor`: `PINK`, `BLUE`, `RED`, `GREEN`, `YELLOW`, `PURPLE`, or `WHITE`. |
| `style` | `SOLID` | Parsed as Bukkit `BarStyle`: `SOLID`, `SEGMENTED_6`, `SEGMENTED_10`, `SEGMENTED_12`, or `SEGMENTED_20`. |
| `autoFade` | `false` | Starts a progress animation after `animation.fade_delay`. Field name is camel-case exactly as read by the implementation. |
| `initialProgress` | `1.0` | Starting progress for the entry. Clamped to the inclusive range `0.0..1.0`. |

There is currently no config field for flags. `BossbarMessage` supports a set of Bukkit `BarFlag` values internally, and the handler can map them, but `BossbarRegistry` never reads or supplies flags; all configured messages therefore have an empty flag set.

### Reload semantics

The registry loads once in the `BossbarHandler` constructor. There is no command or file watcher. Editing `local/bossbars.yml` or the animation config does not rebuild the message list. Animation values are read when each fade starts, but definitions/durations/colors/styles require feature reload/re-enable.

## Empty registry fallback

When no configured messages exist:

- `startMessageCycle()` returns without scheduling rotation;
- `showBossbar()` calls `registry.get(0)`, which returns a synthetic fallback message:
  - message key `default`;
  - duration `100` ticks;
  - white;
  - solid;
  - progress `1.0`;
  - no auto-fade/flags.

The displayed localization key is therefore `bossbar.default`. The default feature message map only defines `bossbar.text1` and `bossbar.text2`, so operators should add `bossbar.default` when intentionally running with an empty registry or expect localization fallback behaviour.

## Localization and placeholders

For every player and every message transition, the title is built through:

```text
feature.getLocalizationHandler()
  .getMessage("bossbar." + messageKey)
  .forAudience(player)
  .build()
```

This provides per-player language/audience resolution supported by the shared localization system. The handler itself does not explicitly call `PlaceholderAPIHook`, register a PAPI expansion, or schedule independent placeholder refreshes.

A title is rebuilt only when:

- the bar is first shown to a player; or
- the message cycle advances and `updateBossbar` is called.

Auto-fade changes progress only; it does not rebuild titles. Placeholders whose values change during a long message remain unchanged until the next message transition unless the localization pipeline returns a dynamic component (normally it does not).

Default localization keys supplied by the feature:

- `bossbar.text1`
- `bossbar.text2`

No variables are injected directly by Bossbar.

## Player lifecycle events

`BossbarListener` uses Bukkit's default `NORMAL` priority and does not specify cancellation handling.

### `PlayerJoinEvent`

Creates a new Adventure boss bar from the current message index and calls `player.showBossBar(bar)`, then stores it by UUID.

The join path does not check for an existing map entry. Under normal Bukkit lifecycle there is no duplicate, because quit removes it. A plugin-driven duplicate invocation could replace the map reference without hiding the previously shown instance.

### `PlayerQuitEvent`

Removes the UUID entry and calls `player.hideBossBar(bar)` when one was tracked.

There are no world-change, respawn, death, vanish, permission-change or locale-change listeners. The same bar remains attached across those states, and locale changes are reflected only at the next message transition or a newly shown bar.

## Initialization and current-message ordering

Feature initialization performs:

1. construct `BossbarHandler` and load `local/bossbars.yml`;
2. register the join/quit listener;
3. call `initOnlinePlayers()`, showing a bar for every player already online;
4. call `startMessageCycle()`.

When messages exist, `startMessageCycle()` calls `scheduleNextMessage()` immediately. That method first updates every active bar to the current entry, then optionally starts fading, then schedules the index advance.

Existing players are therefore shown once by `initOnlinePlayers()` and immediately updated again to the same current message when the cycle starts. The duplicate update is harmless but relevant when localization/placeholders are expensive.

Players joining mid-message receive the current entry at its configured `initialProgress`, not the current faded progress. The active global fade task will include their newly added bar in subsequent steps, so they jump into the fade at the next update.

## Message rotation

For a non-empty registry, `scheduleNextMessage()` recursively schedules itself:

1. select `registry.get(currentMessageIndex)`;
2. update each currently tracked player/bar;
3. start auto-fade when enabled;
4. schedule a delayed callback after `durationTicks`;
5. callback advances:

```text
currentMessageIndex = (currentMessageIndex + 1) % max(1, totalMessages)
```

6. callback invokes `scheduleNextMessage()` again.

The task manager owns every delayed/repeating task. There is no separate cycle handle or running flag. Reload/disable relies on lifecycle task cancellation to stop recursive scheduling.

A player UUID whose Bukkit player cannot be resolved during a transition is skipped but not removed from `activeBossbars`; normal quit cleanup is expected to remove it.

## Bar property updates

At each transition, `updateBossbar()` applies:

1. localized name/title;
2. mapped Adventure color;
3. mapped Adventure overlay;
4. configured initial progress;
5. removal of every Adventure boss-bar flag;
6. addition of mapped message flags.

Resetting all flags prevents a flag from a previous message from leaking into the next. Current config-loaded messages always have no flags.

### Color mapping

Bukkit `BarColor` maps one-to-one to Adventure `BossBar.Color`.

### Style mapping

| Bukkit style | Adventure overlay |
|---|---|
| `SOLID` | `PROGRESS` |
| `SEGMENTED_6` | `NOTCHED_6` |
| `SEGMENTED_10` | `NOTCHED_10` |
| `SEGMENTED_12` | `NOTCHED_12` |
| `SEGMENTED_20` | `NOTCHED_20` |

### Internal flag mapping

If programmatic messages ever supply flags:

| Bukkit flag | Adventure flag |
|---|---|
| `CREATE_FOG` | `CREATE_WORLD_FOG` |
| `DARKEN_SKY` | `DARKEN_SCREEN` |
| `PLAY_BOSS_MUSIC` | `PLAY_BOSS_MUSIC` |

## Auto-fade algorithm

For an auto-fading entry:

```text
stepsPerSecond = max(1, configured steps)
fadeDelay = max(0, configured delay)
duration = message.durationTicks
```

If `duration <= fadeDelay`, fading is skipped.

Otherwise:

```text
fadeDuration = duration - fadeDelay
seconds = max(1, fadeDuration / 20)       // integer floor
 totalSteps = max(1, seconds * stepsPerSecond)
timePerStep = max(1, fadeDuration / totalSteps)
```

After `fadeDelay` ticks, a repeating task starts. On every run:

```text
currentStep = counter++
progress = 1.0 - currentStep / totalSteps
progress is clamped to 0..1
```

The computed progress is assigned to every currently active boss bar, not just bars that were present when the fade started.

The task cancels itself when `currentStep >= totalSteps`. Because progress is applied before the cancellation check, the final invocation applies zero progress.

### Important fade semantics

- Fade always begins mathematically from `1.0`, even when the message's `initialProgress` is lower. The first fade step sets progress to `1.0`, potentially jumping upward.
- `fadeDuration / 20` floors partial seconds, but is clamped to at least one second for step-count purposes.
- Integer division can make `timePerStep * totalSteps` shorter than `fadeDuration`, so the bar may reach zero before the message changes.
- Multiple fade tasks can overlap when durations/fade timings or lifecycle scheduling allow the next message to start while a prior repeating task remains active. There is no per-message generation token cancelling the previous fade at transition time.
- Every fade task updates all bars without checking whether its message is still current. Overlap can therefore cause competing progress writes.
- Progress resets to the next message's `initialProgress` during transition before a new fade begins.

These details should be considered when selecting durations and steps. Use durations comfortably larger than delays and test for overlap.

## Concurrency and thread model

The active-bar map is a `ConcurrentHashMap`, and message index is a plain integer. Bukkit/Adventure player and boss-bar mutations are expected to occur through lifecycle tasks and events on the server thread.

`AtomicInteger` and `AtomicReference<BukkitTask>` are used inside fade setup so the repeating callback can count and cancel its own task. They do not make arbitrary off-thread boss-bar mutations safe.

There is no asynchronous I/O.

## Persistence, database and messaging

Bossbar has no:

- DataProvider/database integration;
- Redis/plugin messaging;
- proxy-side counterpart;
- player preference persistence;
- API service registration;
- PlaceholderAPI expansion.

The only durable inputs are normal config/localization files. Runtime index, bars and animation state reset on reload/restart.

## Disable and cleanup

`disable()` calls `removeAllBossbars()`:

1. iterate tracked UUID/bar entries;
2. resolve each current Bukkit player;
3. hide the bar when the player is still resolvable;
4. clear the concurrent map.

Framework lifecycle cleanup must cancel the recursive delayed cycle and all fade tasks. The handler itself does not retain task handles or explicitly cancel them in `removeAllBossbars()`.

If a lifecycle bug allowed a callback after disable, it would find an empty map for updates but could continue scheduling; therefore all tasks must remain feature-scoped.

## Developer source map

- Defaults, initialization and disable: `features/bossbar/Bossbars.java`
- Message/config loading: `features/bossbar/internal/BossbarRegistry.java`
- Message model: `features/bossbar/internal/BossbarMessage.java`
- Runtime bar/cycle/fade logic: `features/bossbar/internal/BossbarHandler.java`
- Join/quit wiring: `features/bossbar/listener/BossbarListener.java`
- Message-model tests: `src/test/.../features/bossbar/internal/BossbarMessageTest.java`
- Metadata: `features/bossbar/meta/Meta.java`

## Operational verification

1. Enable with players already online and verify one visible bar per player.
2. Join/quit repeatedly and check that no duplicate/orphan bars remain.
3. Configure every color and overlay style and verify exact mapping.
4. Test audience-specific localization with players using different languages.
5. Test an empty `messages` section and provide/verify `bossbar.default`.
6. Verify rotation order follows config child order and durations use ticks.
7. Change `initialProgress` below/above bounds and confirm clamping.
8. Test auto-fade with zero delay, non-zero delay, delay equal to duration, short partial-second durations, and multiple step densities.
9. Test `initialProgress < 1.0` with auto-fade and observe the documented jump to 1.0.
10. Configure transitions likely to overlap and verify whether old/new fade tasks compete.
11. Join during an active fade and verify the bar starts at initial progress and joins subsequent global steps.
12. Disable/reload during delayed and repeating animation tasks and verify lifecycle cancellation leaves no bar/task activity.

## Troubleshooting

- **No bar appears:** verify the feature is enabled; no permission is required. Check for another plugin hiding Adventure boss bars.
- **Bar shows missing/default text:** ensure `bossbar.<message_key>` exists for every configured entry; empty registries use `bossbar.default`.
- **Config edits do not appear:** message definitions load once; reload/re-enable the feature.
- **Fade density uses five instead of twenty:** a missing/unreadable runtime node falls back to `5`, despite the generated default being `20`.
- **Progress jumps upward at fade start:** the fade formula starts from `1.0` independently of `initialProgress`.
- **Progress flickers or moves backward:** overlapping fade tasks may be writing to all active bars. Increase duration separation or harden the implementation with generation/cancellation fencing.
- **Flags in YAML do nothing:** the registry currently does not read flags; internal mapping exists only for programmatically supplied message objects.
- **Placeholder values look stale:** titles update at message transitions, not on fade steps or a dedicated refresh interval.
- **Different worlds/ranks should see different bars:** no eligibility filter exists; add explicit audience policy rather than relying on undocumented behaviour.
