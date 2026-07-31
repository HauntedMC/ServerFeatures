# Glow

> Paper · Feature name `Glow` · feature package `features.glow` · disabled by default

Glow lets players select one of sixteen static Minecraft team colours or two animated effects through a six-row GUI. Selection is persisted by canonical DataRegistry player ID in MySQL. On join, the persisted effect is restored only when the player still holds both the global use permission and the effect-specific permission. Runtime presentation is delegated to the shared scoreboard HUD `ScoreboardManager`.

The effect registry and menu layout are hard-coded; the feature config contains only `enabled`. There is no PlaceholderAPI expansion, Redis synchronization, proxy message, per-server effect setting, command to target other players, or configurable animation interval.

## Commands and permissions

Command root: `/glow`, no aliases.

| Syntax | Permission behaviour | Sender | Effect |
|---|---|---|---|
| `/glow` | Requires `serverfeatures.feature.glow.use` | Player only | Opens the selection menu. |
| `/glow remove` | Command branch itself does not pre-check permission, but `GlowHandler#removeGlow` requires `serverfeatures.feature.glow.use` | Player only | Removes the scoreboard glow and persists disabled state. |

Console receives `general.player_command`.

Tab completion suggests only `remove` for the first argument. Unknown arguments fall through to opening the menu rather than returning `glow.usage`.

### Global permission

```text
serverfeatures.feature.glow.use
```

Required to:

- open/show effect choices;
- activate any effect;
- remove an effect;
- restore a persisted effect on join.

### Effect permissions

Static colours:

```text
serverfeatures.feature.glow.effect.black
serverfeatures.feature.glow.effect.dark_blue
serverfeatures.feature.glow.effect.dark_green
serverfeatures.feature.glow.effect.dark_aqua
serverfeatures.feature.glow.effect.dark_red
serverfeatures.feature.glow.effect.dark_purple
serverfeatures.feature.glow.effect.gold
serverfeatures.feature.glow.effect.gray
serverfeatures.feature.glow.effect.dark_gray
serverfeatures.feature.glow.effect.blue
serverfeatures.feature.glow.effect.green
serverfeatures.feature.glow.effect.aqua
serverfeatures.feature.glow.effect.red
serverfeatures.feature.glow.effect.light_purple
serverfeatures.feature.glow.effect.yellow
serverfeatures.feature.glow.effect.white
```

Animated:

```text
serverfeatures.feature.glow.effect.rainbow
serverfeatures.feature.glow.effect.hauntedmc
```

Both GUI permission gating and `setGlow` enforce the effect permission. A direct Java caller cannot bypass the handler check.

## Complete configuration reference

File: `plugins/ServerFeatures/features/Glow/config.yml`.

| Key | Default | Meaning |
|---|---:|---|
| `enabled` | `false` | Enables DataProvider/ORM startup, effect registry/handler, join/quit listener, GUI command and online-player restoration. |

No effect list, permission, colour sequence, interval, menu slot, persistence toggle or database connection name is configurable.

## Required integrations and startup ordering

Initialization:

1. initialize DataProvider for `Glow`;
2. register MySQL connection identifier `glowOrmConnection` with access policy `player_data_rw`;
3. create ORM context containing `PlayerGlowStateEntity`;
4. throw `IllegalStateException` when ORM creation fails;
5. construct hard-coded `GlowRegistry`;
6. construct `GlowHandler`, which immediately schedules animation tick every one second;
7. construct `GlowStateService`, which requires DataRegistry;
8. register `GlowListener`;
9. register `/glow`;
10. call `initializePlayer` for players already online.

Hard requirements:

- DataProvider MySQL access `player_data_rw`;
- DataRegistry API;
- shared scoreboard HUD/`ScoreboardManager` functioning correctly.

There is no degraded in-memory mode when database/DataRegistry is unavailable.

## Database schema and identity

Table: `player_glow_states`

| Column | Mapping | Meaning |
|---|---|---|
| `player_id` | primary key, no generated value | Canonical DataRegistry numeric player ID. |
| `enabled` | non-null boolean | Whether persisted glow should be restored. |
| `effect_id` | nullable string length 100 | Lowercase effect registry ID, null when disabled. |

Persistence is network/player-scoped by canonical ID; there is no backend/server column. A selection made on one backend is eligible for restoration on every backend running the same feature/database and permissions.

### Save flow

`setGlow` and `removeGlow` call `saveGlowState(Player, Optional<GlowEffect>)`.

1. `PlayerIdentityResolver.findActiveByUuid` performs a synchronous active-identity lookup;
2. when absent, save silently does nothing;
3. when present, `ORMContext#runInTransaction` executes immediately in the caller's context;
4. query row by player ID;
5. create when absent;
6. enabled selection stores lowercased effect ID;
7. removal stores `enabled=false`, `effect_id=null`;
8. only new entity uses `session.persist`; existing managed entity is dirty-checked.

The service does not explicitly schedule save asynchronously. GUI/command calls normally execute on the server thread, so database transaction latency can affect the tick unless ORMContext internally offloads (the service treats it synchronously).

A player whose DataRegistry identity is not currently active can see the runtime glow applied/removed while persistence is silently skipped.

### Restore flow

`DataRegistryIdentityGate.runWhenReady` gates join/online initialization. Once identity is ready:

1. query row in ORM transaction;
2. require row and `enabled=true`;
3. require non-blank `effect_id`;
4. resolve ID in current registry;
5. call `GlowHandler#restoreGlow`;
6. require current global/effect permissions;
7. add runtime state and apply colour without saving.

Unknown effect IDs, removed permissions or disabled state are silently ignored. The database row is not corrected/disabled when restoration is skipped.

## Effect registry and ordering

`GlowRegistry` uses a `LinkedHashMap` and registers:

1. sixteen static `NamedTextColor` effects in fixed order;
2. `rainbow`;
3. `hauntedmc`.

Registry lookup is case-insensitive using lowercase `Locale.ROOT`. Programmatic registration with an existing lowercase ID replaces the value while retaining normal linked-map key order semantics.

The menu supports 28 option slots, while the current registry has 18 effects.

### Static colour definitions

Each static effect:

- ID: Adventure colour string lowercase;
- permission: `serverfeatures.feature.glow.effect.<id>`;
- display name: prettified English ID (`Dark Blue`, etc.);
- animated: false;
- colour: constant `NamedTextColor`;
- icon: mapped concrete block.

Icon mapping includes both `BLUE` and `AQUA` using `LIGHT_BLUE_CONCRETE`; dark shades map to their nearest concrete material.

### Rainbow sequence

One step per epoch second:

1. red
2. gold
3. yellow
4. green
5. aqua
6. blue
7. light purple
8. white
9. gray
10. dark gray
11. dark red
12. dark purple
13. dark blue
14. dark aqua
15. dark green
16. black

Icon: `BEACON`.

### HauntedMC sequence

Alternates one step per epoch second:

1. gold
2. aqua

Icon: `GHAST_SPAWN_EGG`.

## Animation phase and tick behaviour

`GlowHandler` schedules a repeating feature-owned task every one second.

For every active map entry:

- skip null/static effects;
- resolve local online player by UUID;
- calculate `now = Instant.now().getEpochSecond()`;
- pass epoch seconds directly as `elapsedSeconds`;
- apply `effect.colorAt(player, now)` through `ScoreboardManager.setGlow`.

Despite interface comments saying elapsed time since activation, animation uses absolute Unix epoch seconds. Therefore:

- all players/effects are globally phase-synchronized;
- selection time does not start a private sequence at step zero;
- server restarts preserve apparent phase based on wall clock;
- clock changes can jump/reverse phase;
- `Math.max(0, now)` is effectively redundant for modern epochs.

When initially selecting/restoring, `applyNow` calls `colorAt(player, 0)`, so animated effects start at their first colour and may jump to the global epoch phase on the next one-second tick.

Static effects are not reapplied by the tick. If another plugin removes/changes the player's scoreboard team/colour, static glow can remain incorrect until restore/reselection; animated glow is reapplied every second.

## Shared scoreboard interaction

Glow presentation uses:

```java
ScoreboardManager.setGlow(player, NamedTextColor)
ScoreboardManager.removeGlow(player)
```

The player is tracked in a feature-local map, but team/entity-glowing details are owned by the shared scoreboard API.

Potential interactions:

- Nametags, tab list, scoreboard or external team plugins may reassign teams or colour.
- `removeGlow` relies on shared manager to remove only glow ownership without destroying unrelated team metadata.
- Animated updates can overwrite colour changes from another subsystem every second.
- The feature does not listen for scoreboard/team resets or world changes.

The player entity's actual glowing flag/team mechanics should be verified through the shared manager implementation.

## GUI layout and behaviour

Six-row (`54`) `SimpleMenu`, with filler items and no back button.

### Effect grid

Rows 1–4 (zero-based), columns 1–7: 28 option slots:

```text
10..16, 19..25, 28..34, 37..43
```

First row and outer columns are filler/margins.

### Last-row controls

- slot 46 (`row 5,col 1`): remove, `MILK_BUCKET`, 150 ms cooldown;
- slot 49 (`row 5,col 4`): current-status `CLOCK`;
- slot 52 (`row 5,col 7`): close `BARRIER`, 150 ms cooldown.

Effect click cooldown: 120 ms. Selection closes the menu.

### Locked/visibility behaviour

Effect item:

- `visibleWhen` requires global use permission;
- `.permission(effect.permission())` protects activation;
- locked replacement is `BARRIER` with locked lore;
- handler rechecks both permissions.

The command already requires global use permission before opening, so `visibleWhen` is defensive.

The status item is a snapshot built when menu opens. Animated display name is effect name, not current colour, and it does not refresh while open.

### Remove behaviour

GUI/command determine `had = hasActiveGlow` before calling remove. `removeGlow` returns true whenever global permission passes, even when no active runtime effect existed. Therefore caller can distinguish `glow_removed` versus `no_active_glow` with `had`.

Removal always persists disabled state if active identity exists.

## Messages and variables

| Key | Variables |
|---|---|
| `glow.usage` | none; currently not used by command's unknown-argument path |
| `glow.glow_set` | `{color}` effect display component |
| `glow.glow_removed` | none |
| `glow.no_active_glow` | none |
| `glow.menu.title` | none |
| `glow.menu.color.name` | `{color}` |
| `glow.menu.color.lore.allowed` | none |
| `glow.menu.color.lore.locked` | none; also nested as reason in `general.no_permission_reason` |
| `glow.menu.remove.name` / `.lore` | none |
| `glow.menu.close.name` / `.lore` | none |
| `glow.menu.status.active` | `{color}` |
| `glow.menu.status.inactive` / `.lore` | none |

Effect names are hard-coded English Adventure components, not localization keys.

## Player lifecycle events

### Join

```java
@EventHandler(priority = EventPriority.MONITOR)
```

Calls `initializePlayer`, which gates on DataRegistry readiness and restores from database. `ignoreCancelled` is not relevant for join.

Players online during feature enable receive the same initialization loop.

There is no generation/version token preventing a late restore from applying after the player manually changes/removes glow during an in-flight restore. Identity gate/lifecycle implementation should be tested for stale completion ordering.

### Quit

Default priority `NORMAL`:

- call `removeGlowTransient`;
- remove scoreboard glow;
- remove active map entry;
- do **not** update database.

Persisted selection remains enabled for next join.

There is no death, respawn, world-change, permission-change or backend-transfer listener.

## Runtime state

`ConcurrentHashMap<UUID, GlowEffect> activeEffects` is authoritative for menu/status/animation.

- Selection replaces existing effect atomically by key.
- Restoration adds only when permission passes.
- Transient quit cleanup removes.
- Disable currently does not iterate/remove active glows or clear the map.

The concurrent map permits safe iteration/modification, but Bukkit/scoreboard operations are still expected on main/lifecycle threads.

## Disable and shutdown caveat

`Glow#disable()` contains no cleanup.

Framework cleanup cancels the animation task, listener and command/data scopes, but feature code does not:

- call `ScoreboardManager.removeGlow` for online players;
- clear `activeEffects`;
- persist anything;
- explicitly cancel pending restores.

Depending on shared ScoreboardManager/lifecycle cleanup, visible glow/team state can remain after feature disable until another system changes it or player quits. This should be tested and is a likely hardening opportunity.

Database state intentionally remains enabled.

## Persistence, messaging and API summary

- MySQL connection: `glowOrmConnection` / `player_data_rw`.
- Table: `player_glow_states`.
- Identity: DataRegistry numeric player ID.
- Server scope: no server column; shared selection.
- Redis/proxy messaging: none.
- PlaceholderAPI: none.
- Public registered API: none; handlers/services are accessible from feature instance only.

## Developer source map

- Integration/lifecycle: `features/glow/Glow.java`
- Runtime map/animation: `features/glow/internal/GlowHandler.java`
- Effect interface/implementations/registry: `features/glow/effect/`
- Persistence: `features/glow/service/GlowStateService.java`
- Entity: `features/glow/entity/PlayerGlowStateEntity.java`
- Join/quit: `features/glow/listener/GlowListener.java`
- GUI: `features/glow/menu/GlowMenu.java`
- Command: `features/glow/command/GlowCommand.java`
- Effect/service tests: `src/test/.../features/glow/`

## Operational verification

1. Verify DataProvider/DataRegistry startup failure paths.
2. Test all 18 effects and exact permission nodes.
3. Confirm static icon/name/colour mappings and menu positions.
4. Observe animated initial first colour followed by epoch-synchronized phase.
5. Select/remove with active and unavailable DataRegistry identity; inspect persistence.
6. Restart/switch backend and confirm shared player-ID restore.
7. Remove effect permission/global permission before join and verify silent non-restore without DB cleanup.
8. Test join/manual-change ordering during delayed identity readiness.
9. Test conflict with Nametags/Scoreboard/external team plugins; compare static vs animated repair.
10. Disable feature with online glowing players and verify whether shared lifecycle removes visible state.
11. Test database latency on GUI click/command because save transactions are called synchronously.
12. Quit/rejoin and verify quit does not disable persisted selection.

## Troubleshooting

- **Glow is lost after join:** check global/effect permission, DataRegistry identity, DB row/effect ID and registry ID.
- **Selection works but is not saved:** active identity lookup may have returned empty.
- **Animated colour starts then jumps:** first application uses elapsed zero; tick uses epoch seconds.
- **All players animate together:** intentional current epoch-phase implementation.
- **Static glow is overwritten by another plugin:** static effects are not periodically reapplied.
- **Menu says locked despite granted permission:** verify exact underscore-based effect permission ID.
- **Glow remains after disabling feature:** no explicit disable cleanup exists.
- **Database action causes tick delay:** save/restore service invokes ORM transaction directly; inspect ORM threading/latency.
- **Effect names cannot be translated:** hard-coded effect display components, not localization keys.
