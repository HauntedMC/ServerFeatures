# Scoreboard

> Paper · Feature ID `scoreboard` · disabled by default · fixed 15-line localized sidebar

Scoreboard renders a per-player sidebar through ServerFeatures' shared `ScoreboardManager`. The feature owns content generation, periodic refresh, snapshot deduplication, join/quit lifecycle and failure isolation. The shared HUD manager owns the underlying Bukkit objective/team representation.

There is no player command, toggle, permission filter, world filter, database state or PlaceholderAPI expansion registered by this feature.

## Configuration

Feature configuration:

| Key | Default | Meaning |
|---|---:|---|
| `enabled` | `false` | Enables the periodic updater and join/quit listeners. |
| `refresh_interval` | `100` ticks | Period between complete per-player content evaluations. Also used as the repeating task period with an initial delay of zero. |

`refresh_interval` is read with a direct cast to `int`. It is not clamped or type-normalized by `ScoreboardHandler`; a wrong type or scheduler-invalid value can fail feature initialization/task scheduling.

Content is provided through localization messages rather than a list in config:

- `scoreboard.title`;
- `scoreboard.line1` through `scoreboard.line15`.

Default content is `[Title]`, `empty1`, `empty2`, `empty3`, then empty strings for lines 4–15.

There are no config keys for eligibility, line count, line length, duplicate handling, ordering, objective name, score values or placeholder refresh cadence. The line count is hard-coded to 15.

## Commands, permissions and placeholders

Scoreboard registers:

- no command;
- no permission;
- no per-player opt-out;
- no PlaceholderAPI identifier of its own;
- no public feature service.

Each update creates one player-message context, resolving the audience language once for the title and all lines. The normal ServerFeatures localization pipeline still applies independently to every dynamic message. Messages whose raw template contains no `%` token are cached as components per language and key; `%...%` templates continue through PlaceholderAPI for every player and refresh.

## Initialization and player lifecycle

On feature initialization:

1. construct `ScoreboardHandler` and read `refresh_interval`;
2. schedule the repeating updater immediately;
3. register the join/quit listener;
4. immediately call `updateScoreboardSafely` for every player already online.

Existing players are initialized independently so one broken expansion/provider cannot abort initialization for the rest.

### Join

`PlayerJoinEvent` is handled at `NORMAL` with `ignoreCancelled=true`. The listener immediately calculates and pushes one scoreboard snapshot.

There is no delayed join-settle period. Placeholder providers that require later identity/world readiness may fail or return fallback values on the first evaluation; the repeating updater can correct the content later.

### Quit

`PlayerQuitEvent` is handled at `NORMAL` with `ignoreCancelled=true`. The feature:

- removes the player's cached snapshot;
- removes all warning-throttle entries for that UUID;
- asks `ScoreboardManager` to remove the sidebar.

### Disable

Disable clears all snapshots and warning state, then independently removes the sidebar for every player currently online. Scheduled-task/listener cancellation is delegated to the feature lifecycle manager.

## Rendering algorithm

A complete update for one player performs this sequence:

1. Resolve the player's language once and create a player-message context.
2. Render `scoreboard.title` through that context, reusing its cached component when static.
3. If title rendering fails, use `Component.empty()`.
4. Iterate the precomputed `scoreboard.line1` through `scoreboard.line15` keys in ascending order.
5. Render each line independently, reusing cached components for static templates.
6. Skip a line whose rendering failed.
7. Serialize the rendered component to plain text through a shared serializer.
8. If the plain text starts with literal `<end>`, stop scanning immediately and do not include that line.
9. Otherwise append the component, including empty components/empty strings.
10. Create an immutable `ScoreboardSnapshot(title, lines)`.
11. Compare it with the last snapshot for this UUID.
12. If equal, do nothing.
13. If different, call `ScoreboardManager.updateSidebar(player, title, lines, previousLines)` and cache the new snapshot.

### `<end>` sentinel

The sentinel check is performed **after** localization/component rendering and plain serialization. It is a prefix check:

```text
plain.startsWith("<end>")
```

Therefore:

- `<end>` stops the list;
- `<end> anything` also stops it;
- leading spaces before `<end>` prevent recognition;
- formatting is removed by plain serialization before checking;
- the sentinel line itself is not shown;
- later message keys are not rendered.

Empty lines do not terminate the scoreboard and are passed to the shared manager. Their uniqueness/score-entry encoding is the manager's responsibility.

### Snapshot deduplication

Adventure `Component` equality plus ordered line-list equality determine whether an update is necessary. When all rendered components are equal to the previous snapshot, no shared-manager call occurs.

The feature passes the previous line list to `ScoreboardManager.updateSidebar`, allowing the manager to remove/update stale line ownership without reconstructing every shared HUD object. The exact Bukkit objective/team implementation belongs to the API manager, not this feature class.

## Periodic refresh and scaling

The updater runs synchronously through `scheduleRepeatingTask` with:

- initial delay `0` ticks;
- period `refresh_interval` ticks.

For every online player it independently looks up the title and up to 15 localized templates. The language/service lookup is performed once per player update rather than once per message. Static templates reuse a component cached per language and key, while templates containing `%` are rebuilt so PlaceholderAPI values remain current. With no early `<end>`, the sentinel check still performs up to `15 × P` cheap plain serializations.

Snapshot deduplication reduces Bukkit/sidebar writes. Static caching additionally removes repeated text normalization, sanitizer setup and MiniMessage deserialization for unchanged non-placeholder templates. The shared all-defaults MiniMessage parser is constructed once rather than once per message.

A low interval combined with expensive PlaceholderAPI expansions can still materially affect the main thread. Dynamic lines must remain synchronous and player-specific; there is no per-line refresh interval.

## Failure isolation and logging

The feature catches both `RuntimeException` and `LinkageError` at multiple boundaries:

- title/line localization build;
- plain component serialization;
- complete per-player update;
- shared sidebar removal;
- iteration across players.

A failed title becomes empty. A failed line is skipped. Failure for one player does not stop updates for other players or later cycles.

Warnings are throttled by this key:

```text
(player UUID, message/operation key, throwable class name)
```

The same key emits at most once every five minutes. Different lines, operations or exception classes have independent windows. The warning includes the full throwable stack trace.

When reading UUID/name itself throws, fallback helpers produce an instance-derived synthetic UUID for throttling and `unknown` for the log name.

Warning state is never globally pruned by time; entries remain until that player quits or the feature disables. At ordinary scale this is bounded by players × failing keys/types.

## Event ordering and thread model

| Path | Thread/priority |
|---|---|
| Periodic updater | Main Bukkit task. |
| Initialization of already-online players | Feature initialization thread, normally main server thread. |
| Join update | `PlayerJoinEvent`, `NORMAL`, ignored when cancelled. |
| Quit cleanup | `PlayerQuitEvent`, `NORMAL`, ignored when cancelled. |

The feature assumes localization, PlaceholderAPI and `ScoreboardManager` calls are safe on the main thread. It does not call Bukkit scoreboard APIs asynchronously.

## Interactions with other scoreboard/team features

This feature assigns a sidebar through the shared `ScoreboardManager`; it does not directly replace `Player#setScoreboard` in the visible implementation. Correct coexistence with Glow, Nametags and other team users therefore depends on the shared manager's ownership model.

The feature itself does not:

- create or name teams/objectives;
- inspect whether another plugin replaced the player's scoreboard;
- reassert ownership on scoreboard-change events;
- merge another plugin's sidebar;
- expose a pause/priority API.

A third-party plugin that replaces the entire player scoreboard can still make the sidebar disappear until a later update causes the manager to write again. If the rendered snapshot remains equal, deduplication may skip that write because this handler does not observe external ownership loss.

## Messages and variables

Scoreboard declares only `scoreboard.title` and `scoreboard.line1..15`. It supplies no explicit `.with(...)` variables in `ScoreboardHandler`.

Any dynamic values must therefore come from:

- localization audience selection;
- global/local message formatting behavior;
- PlaceholderAPI or other processing already integrated into the localization pipeline.

No line-number, score, server or player variables are injected directly by this feature.

## Persistence and network behavior

The only state is in memory:

- last rendered snapshot per UUID;
- warning throttle map.

There is no database table, Redis channel, configuration-generated runtime state or cross-server synchronization. A server switch/rejoin creates a new local sidebar from current messages.

## Important implementation boundaries

- Exactly 15 message keys are scanned; additional `scoreboard.line16` keys are ignored.
- Empty lines are included, not skipped.
- `<end>` is a rendered plain-text prefix sentinel.
- The title falls back to empty on failure; a line failure removes that position from the built list.
- No permission/world/gamemode/vanish eligibility exists.
- No command or player toggle exists.
- Refresh interval is not clamped.
- Dynamic `%...%` localization/placeholder work still runs when the snapshot is unchanged; static component parsing does not.
- External scoreboard replacement is not detected.
- A persistent failure warning is suppressed for five minutes per player/key/type, which can hide repeated occurrences between warnings while preserving one stack trace per window.
- There is no explicit quit check inside a periodic per-player update; it operates on the online collection snapshot supplied by Bukkit.

## Verification checklist

1. Enable with the defaults and verify title plus all 15 effective lines, including empty-line behavior.
2. Place `<end>` at different line positions, with formatting and with leading spaces.
3. Use audience-specific language and PlaceholderAPI values; verify independent per-player rendering.
4. Change one line and confirm stale previous content is removed without flicker.
5. Keep content unchanged and instrument/observe that `ScoreboardManager.updateSidebar` is skipped while placeholders are still evaluated.
6. Make one line expansion throw or linkage-fail; confirm later lines and other players continue.
7. Trigger the same failure repeatedly and validate the five-minute warning throttle.
8. Join during identity/world initialization and confirm the immediate result converges on later refreshes.
9. Let another plugin replace the player's scoreboard and observe whether deduplication prevents automatic recovery until content changes.
10. Reload/disable with online players and confirm all feature sidebars and warning/snapshot state are removed.
11. Load-test the configured interval with representative placeholder cost and player count.

## Source map

- Defaults and lifecycle: `features/scoreboard/Scoreboard.java`
- Rendering, snapshots, scheduling and failure isolation: `features/scoreboard/internal/ScoreboardHandler.java`
- Join/quit event ordering: `features/scoreboard/listener/PlayerJoinListener.java`
- Underlying sidebar implementation: `api/ui/hud/scoreboard/ScoreboardManager`
