# Tablist

> Paper · Feature ID `tablist` · disabled by default · periodic header/footer, display-name and ordering writer

Tablist periodically rewrites three Paper player-list surfaces for every online player:

- the header sent to that player;
- the footer sent to that player;
- the player's own `playerListName`, which other viewers see as that entry;
- `playerListOrder`, calculated from Vault primary group when available.

Content comes from localized message templates and is rebuilt on every refresh. The feature has no command, permission, per-player toggle, database state or PlaceholderAPI expansion of its own.

## Configuration

| Key | Default | Meaning |
|---|---:|---|
| `enabled` | `false` | Enables listener and updater registration. |
| `refresh_interval` | `5` | **Seconds** between refreshes. Initialization multiplies by 20 to obtain ticks. |
| `rank_order` | ordered list below | Primary-group names mapped to numeric priorities starting at 1. |

Default rank order, highest intended priority first:

1. `owner`
2. `admin`
3. `serveraccount`
4. `modt3`
5. `modt2`
6. `modt1`
7. `bouwteamt3`
8. `bouwteamt2`
9. `bouwteamt1`
10. `eventteamt3`
11. `eventteamt2`
12. `eventteamt1`
13. `mediateam`
14. `discordteam`
15. `ambassador`
16. `streamer`
17. `supremeplus`
18. `supreme`
19. `god`
20. `legend`
21. `elite`
22. `premium`
23. `speler`
24. `default`

The list is read once at handler construction. Entries are lower-cased and trimmed. Duplicate normalized names overwrite earlier entries with the later numeric position.

Unknown groups use the configured `default` priority where present; otherwise they use `rankPriorityMap.size() + 1`.

`refresh_interval` is directly cast to `int`, multiplied by 20 and scheduled without a positive-value clamp. Wrong types or invalid/non-positive scheduler periods can fail initialization or scheduling.

## Message templates

| Key | Default purpose |
|---|---|
| `tablist.header` | Two-line HauntedMC heading, website and server-time placeholder. |
| `tablist.footer` | Server name/player counts, player ping and store link. |
| `tablist.prefix` | `%afk_formatted%%vault_prefix%` |
| `tablist.playername` | `%player_name%` in gray. |
| `tablist.suffix` | Voice-chat installation placeholder. |

Each component is built through the localization handler with the subject player as audience. Placeholder processing therefore depends on the shared localization/PAPI pipeline and installed expansions.

The feature injects no `.with(...)` variables itself.

## Commands, permissions and APIs

Tablist registers:

- no command;
- no permission;
- no player opt-out;
- no PlaceholderAPI identifier;
- no public service interface.

`getHandler()` exposes the concrete handler from the feature instance for internal code, with methods to update names, clear header/footer and force a refresh, but it is not registered through the feature API manager.

## Rank resolver selection

At handler construction:

1. check whether plugin name `Vault` is currently enabled;
2. if enabled, obtain Vault's `Permission` provider from Bukkit's services manager;
3. use `VaultRankResolver` only when a provider exists;
4. otherwise use `AlphaRankResolver`.

`VaultRankResolver#getRank` calls `Permission#getPrimaryGroup(player)`, lower-cases a non-empty result and falls back to `default`.

`AlphaRankResolver` always returns `default` and reports `isReady=false`. That causes the handler to use purely alphabetical sorting rather than applying the rank map.

Resolver availability is captured once. Enabling/reloading Vault or its permission provider later does not switch resolver until Tablist is reconstructed.

## Actual sorting behavior

With a ready Vault provider, the code creates this comparator:

```text
compare rank priority ascending
then player name case-insensitive ascending
then reverse the entire comparator
```

Because `.reversed()` applies to the complete comparator, actual ordering is:

- **larger numeric priority first**;
- **names Z→A within equal rank**.

Given the default list, `default`/unknown lower-ranked players are assigned smaller list-order numbers before `owner`, contrary to the source comment and the natural interpretation of the configured list.

Without Vault/provider, ordering is player name A→Z.

After sorting, the feature calls `player.setPlayerListOrder(i)` for indices `0..N-1`. Paper/client presentation then uses those integer orders.

This page documents the current behavior; correcting it would require removing/moving `.reversed()` or explicitly reversing only the intended comparison.

## Refresh algorithm

`refreshAllPlayers()`:

1. copies `Bukkit.getOnlinePlayers()` into a mutable list;
2. sorts it using the selected strategy;
3. iterates the sorted list;
4. sets each subject player's list order to its index;
5. renders and sends that subject's header/footer;
6. renders and sets that subject's list name.

`updateTablist(player)` always rebuilds and sends all components. There is no snapshot comparison or change detection.

`getTablistName(player)` concatenates three independently rendered components:

```text
prefix + playername + suffix
```

No separator is automatically added beyond text present in templates.

## Scheduler and thread model

Initialization schedules `scheduleAsyncRepeatingTask` with:

- initial delay 0 ticks;
- period `refresh_interval × 20` ticks.

Inside that **asynchronous** task the handler calls Bukkit/Paper player APIs:

- `Bukkit.getOnlinePlayers()`;
- Vault permission provider methods;
- localization/PlaceholderAPI rendering;
- `Player#setPlayerListOrder`;
- `sendPlayerListHeaderAndFooter`;
- `Player#playerListName`.

Many Bukkit APIs and third-party PlaceholderAPI/Vault providers are not generally guaranteed safe off the main thread. This is a material implementation boundary. A robust redesign should separate asynchronous data retrieval only where explicitly supported and schedule all Bukkit/player mutations on the main thread.

The updater has no per-player exception isolation. A runtime exception from one player/placeholder/provider escapes `refreshAllPlayers`; depending on scheduler behavior, that invocation—and potentially future repetitions—can fail, leaving stale tab state.

## Join and quit ordering

`TablistListener` uses default `NORMAL` priority and does not specify `ignoreCancelled`.

### Join

`forceRefreshTablist(joiningPlayer)`:

1. clears only the joining player's header/footer;
2. calls `refreshAllPlayers()` synchronously from the join event;
3. recalculates order and content for every online player.

This immediate main-thread refresh can overlap in time with the asynchronous repeating refresh because there is no lock/generation fence.

### Quit

The listener calls `clearTablist(quittingPlayer)`, which sends empty header/footer to the player who is leaving. It does not immediately recalculate the remaining players' order. The next periodic refresh or later join updates order gaps.

No player-name/order state is removed from an internal cache because none exists.

## Enable and disable behavior

Unlike Scoreboard, initialization does not explicitly iterate already-online players. The zero-delay async updater is expected to initialize them.

Disable iterates online players and calls `clearTablist`, which clears header/footer only.

It does **not** restore:

- `playerListName` to null/default;
- `playerListOrder` to zero/default;
- names/orders previously owned by another plugin.

Thus custom names and order values can remain after feature disable until another plugin/server event replaces them.

## Placeholder and integration dependencies

The default templates refer to placeholders likely supplied by:

- PlaceholderAPI Server expansion (`server_time`, `server_name`, max players);
- Vanish/player-count expansion (`vanish_playercount`);
- Player expansion (`player_ping`, `player_name`);
- Vault (`vault_prefix`);
- AFK feature (`afk_formatted`);
- voice-chat integration (`voicechat_installed`).

Tablist does not check whether these expansions are installed. Missing-placeholder behavior is determined by the localization/PlaceholderAPI pipeline and may appear literally or empty.

The rank resolver and `%vault_prefix%` are separate concerns: Vault sorting can fall back alphabetically while the placeholder may independently resolve/fail.

## Viewer versus subject personalization

Header/footer are rendered for and sent to each receiving player, so player-specific ping/language values are natural.

The list name is rendered using the **subject player** as audience and assigned once to that player's profile entry. It is not rendered separately for each viewer. Therefore viewer-specific language/visibility data cannot produce different names for different observers through this feature.

Vanish visibility itself is not managed here; Paper/proxy/vanish features decide whether an entry is visible. Tablist sorts and formats all players returned by the backend's online-player collection, including vanished players.

## Persistence and messaging

All behavior is local/in-memory and reapplied periodically. There is:

- no database;
- no Redis/plugin messaging;
- no persistent preference;
- no cache/snapshot;
- no network-wide ordering contract.

Each backend formats only players connected to that Paper server. A proxy-generated global tab list would be a separate concern.

## Performance

Every refresh performs:

- one copy/sort of all online players: `O(N log N)`;
- one Vault primary-group lookup per player when ready;
- five localized component builds per player (header, footer, prefix, name, suffix);
- three Paper mutation calls per player.

With the default 5-second interval this can be significant if placeholders make database/network calls or the player count is high. There is no deduplication, static-template cache, rank cache or independent header/name intervals.

## Important implementation boundaries

- Refresh interval is seconds, not ticks.
- The updater uses asynchronous Bukkit/player APIs.
- Vault sort is reversed in both rank and name dimensions.
- No per-player failure isolation exists.
- No snapshot/deduplication exists.
- Resolver/config rank map are captured at construction.
- Join triggers a full synchronous refresh for every player.
- Quit does not reorder remaining players immediately.
- Disable clears only header/footer, not names/orders.
- No permissions, viewer conditions or per-player toggles exist.
- Subject list names are not viewer-specific.
- Missing placeholders are not validated.
- Vanished players are not filtered by this handler.

## Verification checklist

1. Enable with Vault/provider and record actual ordering for owner/default and A/Z names.
2. Disable Vault/provider and confirm alphabetical A→Z fallback.
3. Add duplicate, unknown, mixed-case and whitespace rank entries.
4. Verify `refresh_interval` timing and invalid zero/negative/wrong-type behavior in a test server.
5. Instrument thread identity for Vault, PlaceholderAPI and all Player mutation calls.
6. Make one placeholder throw and determine whether the repeating task continues.
7. Join during an active refresh and test concurrent main/async writes.
8. Quit a middle-order player and observe when remaining order is compacted.
9. Disable/reload and inspect header/footer, `playerListName` and `playerListOrder` separately.
10. Test every default placeholder with missing and installed expansions.
11. Test vanished players and per-viewer visibility from Vanish.
12. Load-test placeholder/render cost at production player counts.

## Source map

- Defaults/templates/scheduler/disable: `features/tablist/Tablist.java`
- Sorting and rendering: `features/tablist/internal/TablistHandler.java`
- Rank abstraction: `features/tablist/internal/RankResolver.java`
- Vault provider lookup: `features/tablist/internal/VaultRankResolver.java`
- Alphabetical fallback: `features/tablist/internal/AlphaRankResolver.java`
- Join/quit behavior: `features/tablist/listener/TablistListener.java`
