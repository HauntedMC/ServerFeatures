# Nametags

> Paper · Feature name `Nametags` · feature package `features.nametags` · disabled by default

Nametags renders one packet-only Text Display passenger above every eligible online player. The display is never spawned as a Bukkit world entity: PacketEvents create, mount, update and destroy the fake entity independently for each viewer. A main-thread lifecycle manager combines Paper tracking events, periodic reconciliation, per-viewer generation fencing, transition suspension, passenger-packet guarding and optional remount repair to prevent detached/floating/ghost tags after tracking changes, teleports, world changes, respawns, mounts, gliding, relogs and skin updates.

The text is built from three localized components (`prefix`, `playername`, `suffix`) for the owner. Self-view is enabled by default and persisted by canonical DataRegistry player ID in MySQL. Other visibility rules are local/runtime-only.

## Commands and permissions

Brigadier root: `/nametag`, no aliases.

The root requires:

```text
serverfeatures.feature.nametags.use
```

Self-view subtree additionally requires:

```text
serverfeatures.feature.nametags.command.selfview
```

| Syntax | Sender | Behaviour |
|---|---|---|
| `/nametag selfview` | Player recommended; console receives usage after no status | Shows current status for players, then usage. |
| `/nametag selfview on` | Player only | Enables persistent self-view unless cached value is already true. |
| `/nametag selfview off` | Player only | Disables persistent self-view unless cached value is already false. |
| `/nametag selfview toggle` | Player only | Toggles current effective preference value. |
| `/nametag selfview status` | Player only | Shows current preference. |

`on`/`off` use nullable cache inspection for already-state feedback. Before preference loading completes, cache can be null; the command then accepts the request and the write generation invalidates the pending read so stale DB completion cannot overwrite it.

Self-view suppression while elytra gliding is temporary and does not change the persisted preference. `status` reports the preference, not necessarily current visibility during glide/death/other visibility suppression.

## Complete configuration reference

File: `plugins/ServerFeatures/features/Nametags/config.yml`.

| Key | Default | Exact behaviour and validation |
|---|---:|---|
| `enabled` | `false` | Initializes MySQL/DataRegistry, PacketEvents listener, lifecycle listener, manager tasks and command. |
| `max_distance` | `45` blocks | Visibility distance, inclusive and squared. Read once by `VisibilityManager`; numeric values clamped to at least1, invalid values fallback45. |
| `lifecycle.join_settle_delay_ticks` | `10` | Delay after self-view preference readiness before owner registration. Clamped >=1. |
| `lifecycle.tracking_settle_delay_ticks` | `2` | Delay before per-viewer fake entity spawn after tracking/reconciliation. Clamped >=1. |
| `lifecycle.transition_settle_delay_ticks` | `10` | Delay after world/large-teleport/respawn transition before rotating fake identity and rebuilding. Clamped >=1. |
| `lifecycle.teleport_rebuild_distance` | `64` blocks | Same-world teleport distance threshold; squared comparison, inclusive (`distanceSquared >= threshold²`). Clamped >=1. Cross-world teleport is handled by world-change event instead. |
| `reconciliation.interval_ticks` | `10` | Full owner/viewer reconciliation period. Clamped >=1. |
| `repair.remount_enabled` | `true` | Enables periodic `SET_PASSENGERS` repair for currently visible pairs. Boolean only; invalid values fallback true. |
| `repair.remount_interval_ticks` | `100` | Remount-repair period, clamped >=20. |

There are no configurable lines, offsets, scale, background, billboard, shadow, see-through, entity type, resource-pack requirement, spectator/vanish/disguise rules, self-view default, DB connection, transition event list or packet-listener priority. Those are fixed in code.

## Text and display properties

Each logical `Nametag` creates `NametagPacketProperties` with:

- text from `PlaceholderHook`;
- billboard `CENTER`;
- translation `(0.0, 0.3, 0.0)`;
- shadow enabled;
- see-through disabled;
- no gravity;
- fully transparent black background (`ARGB alpha 0`).

The fake entity receives:

- a generated PacketEvents entity ID;
- random UUID;
- generation counter;
- owner entity ID captured from Bukkit player.

A hard lifecycle transition rotates fake entity ID/UUID and increments generation so late callbacks/packets from the old generation cannot mutate or revive the new display.

### Text composition

For owner audience:

```text
nametags.prefix + nametags.playername + nametags.suffix
```

Default values:

- `nametags.prefix`: `%luckperms_prefix%`
- `nametags.playername`: `%player_name%`
- `nametags.suffix`: empty

The normal localization pipeline is used with the owner as audience, so installed PAPI/shared placeholders supported by localization may resolve. Nametags does **not** register its own PAPI expansion.

On runtime/linkage failure, text falls back to the owner's current Bukkit name and logs a warning. Text is cached in the logical Nametag until a refresh/rebuild path calls `updateNametagText`.

Text refresh occurs on explicit update hooks and skin update rebuilds only when requested. There is no periodic placeholder refresh interval, so changing prefixes/health/ping placeholders can remain stale unless another feature invokes update or owner lifecycle rebuilds.

## MySQL/DataRegistry persistence

Startup:

- DataProvider feature initialization;
- MySQL logical connection `nametagsOrmConnection`;
- access policy `player_data_rw`;
- ORM entity `PlayerNametagEntity`;
- DataRegistry required.

Table: `player_nametags`

| Column | Mapping | Meaning |
|---|---|---|
| `player_id` | primary key `Long` | Canonical DataRegistry player ID. |
| `self_view` | non-null boolean | Persisted preference. |

### Read

`findSelfView(uuidString)`:

1. resolve persisted identity by UUID through DataRegistry;
2. query `SELECT n.selfView ... WHERE playerId = :playerId`;
3. return empty for unknown/no row/errors;
4. manager defaults empty/failure to true.

Preference reads use per-player load-generation and connection-session tokens. Completion is marshalled to lifecycle main task and ignored when player disconnected/reconnected or a newer load/write started.

### Write

`setSelfViewEnabled` immediately updates in-memory preference, reconciles self pair, then calls:

```sql
INSERT INTO player_nametags (player_id, self_view)
VALUES (:playerId, :selfView)
ON DUPLICATE KEY UPDATE self_view = :selfView
```

Identity lookup is asynchronous/persisted by UUID. Unknown identity silently results in no upsert. Errors log warnings but do not revert runtime preference.

`playerName` parameter is passed through API but not used in persistence.

Self-view is network-shared when backends use the same DataRegistry/database; no server column exists.

## Join and registration ordering

`PlayerJoinEvent` at `MONITOR`:

1. DataRegistryIdentityGate waits for canonical identity;
2. manager begins connection session token;
3. async self-view preference load begins with load-generation token;
4. on completion/fallback, schedule main-thread continuation only if plugin/player/session still current;
5. schedule registration after `join_settle_delay_ticks`;
6. registration removes any existing logical owner tag;
7. creates fresh fake identity/default metadata;
8. registers attachment mapping owner→fake;
9. reconciles owner's tracked viewers and the joining player as viewer.

Players online when feature initializes go through the same path.

The delay avoids spawning into a client whose world/entity tracking has not settled. Generation/session fencing prevents a delayed registration from an earlier connection from reviving after relog.

## Per-viewer state machine

Each logical owner maintains `NametagViewerState` by viewer UUID with:

- generation token;
- spawned/hidden state;
- one pending spawn BukkitTask.

### Ensure shown

1. if marked spawned and attachment index says visible, return;
2. if state disagrees with attachment index, invalidate and retry;
3. if a spawn task is already pending, return;
4. increment viewer generation;
5. capture owner fake-entity generation;
6. schedule delayed spawn;
7. replace/cancel any prior pending task.

At completion, it validates:

- viewer-state generation;
- fake-entity generation;
- registry still points to same logical Nametag;
- viewer-state object still current;
- viewer online and all visibility rules still pass.

Only then does it emit packet bundle and mark spawned/visible. Failure marks hidden, removes state and logs.

### Hide

- increment state generation;
- cancel pending spawn;
- mark hidden in attachment index;
- remove state;
- send destroy packet when aggressive or previously spawned.

Destroy is attempted twice before a warning. Sending destroy for unknown entity is intentionally harmless and used to remove ghosts.

## Packet spawn transaction

`NametagUpdater.spawn` validates owner/viewer online, then sends one bundle:

1. opening bundle delimiter;
2. fake Text Display creation packet at owner;
3. `SET_PASSENGERS` for owner containing all current Bukkit passenger entity IDs plus fake tag ID;
4. full metadata packet;
5. closing bundle delimiter.

If any send fails, the closing delimiter is attempted and a destroy packet is sent for partial cleanup before rethrow.

The fake nametag is appended after all real current passengers. Mount/passenger mutations trigger remount one tick later.

## Passenger/destroy packet guard

A PacketEvents listener at `HIGHEST` intercepts outgoing packets per viewer:

- `SET_PASSENGERS`: when attachment index says that owner/fake is currently visible to this viewer, append missing fake passenger without removing other passengers;
- `DESTROY_ENTITIES`: when an owner entity is destroyed for the viewer, append related visible fake nametag IDs.

This protects against vanilla/other-plugin passenger replacement silently detaching the tag and ensures owner destruction also removes fake display client-side.

The attachment index is the packet listener's authoritative per-viewer visibility map; manager state and index are continuously reconciled.

## Visibility rules

Base `shouldShow` requires:

- viewer/owner non-null and online;
- neither dead;
- owner not suspended during transition/death;
- same world;
- for other-view: owner Paper tracking set contains viewer;
- for self-view: preference true and not glide-suppressed;
- all visibility conditions pass.

Conditions run in order:

1. `DeathCondition`: owner not dead;
2. `GsitCondition`: owner neither mounted on nor carrying `AreaEffectCloud` seat marker;
3. `DistanceCondition`: same world and distance <= configured max;
4. `DisguiseCondition`: included only if LibsDisguises was enabled at manager construction; hides any disguised owner;
5. `VanishCondition`: `viewer.canSee(owner)`;
6. `SpectatorCondition`: owner gamemode not spectator.

Any condition exception/linkage error fails closed (hidden) and logs at most once per condition type per30 seconds.

Disguise integration inclusion is startup-snapshotted. Vanish uses Bukkit visibility, allowing staff viewers who can see vanished owner to see their tag while ordinary viewers cannot.

Self-view still passes the same distance/visibility rules; owner-to-self distance is zero, but spectator/death/GSit/disguise/vanish semantics can hide it.

## Tracking events and periodic reconciliation

### Track

`PlayerTrackEntityEvent`, `MONITOR`, `ignoreCancelled=true`: schedule pair reconciliation with tracking-settle delay when tracked entity is player.

### Untrack

`PlayerUntrackEntityEvent`, `MONITOR`: wait one tick, then distinguish stale untrack from current generation:

- if owner is tracked again and current attachment remains visible, ignore late untrack;
- otherwise aggressively hide;
- when tracked again after hide, schedule reconciliation.

### Full reconciliation

Every `reconciliation.interval_ticks` on main thread:

- remove logical nametags whose owner is offline;
- for every owner, reconcile each `owner.getTrackedBy()` viewer;
- always consider self pair;
- remove/hide states not in current candidate set.

This repairs missed/third-party event drift and stale viewer bookkeeping.

## Transition lifecycle

### Same-world teleport

`PlayerTeleportEvent`, `MONITOR`, `ignoreCancelled=true`:

- cross-world returns and relies on `PlayerChangedWorldEvent`;
- when squared distance >= configured threshold, begin transition.

Small teleports rely on tracking/reconciliation without rotating fake identity.

### Changed world / respawn

Begin transition:

1. issue transition generation token;
2. cancel prior transition task;
3. suspend owner's tag: cancel viewer tasks, unregister attachment, broadcast destroy to all online players, clear states;
4. remove transitioning player as viewer from every owner;
5. after settle delay, validate token/online;
6. rotate fake ID/UUID and update owner entity ID;
7. register new attachment;
8. unsuspend;
9. reconcile owner and viewer.

If logical nametag is absent, register fresh.

### Death

Immediately cancels transition, suspends owned tag and removes dead player as viewer. Respawn starts delayed transition rebuild.

### Skin update

`SkinUpdateEvent` rebuilds owner after10 ticks with `refreshText=false`. Fake identity rotates; text remains cached.

### Resource pack success

Destroys/re-evaluates every tag for that viewer, useful when fonts/textures become available after pack load.

### Gamemode change

One tick later reconcile owner and viewer to apply spectator transitions after Bukkit state updates.

### Mount/dismount

One tick later remount visible viewers and separately call `updateNametag` with default properties, causing owner/viewer reconciliation.

### Glide

Tracks self-view suppression while gliding; entering hides self pair, leaving restores according to persisted preference. Other viewers continue seeing tag.

## Remount repair

When enabled, every configured interval:

- iterate logical nametags not suspended;
- for every spawned viewer state still passing visibility, resend `SET_PASSENGERS` with current real passengers + fake tag;
- hide/invalidate stale viewers.

This is a safety net for packet ordering/plugin passenger changes. It adds periodic packet traffic proportional to visible owner-viewer pairs.

## Text/update API surface

No formal API service is registered, but Java integrations use manager methods:

- `updateNametag(player, UpdateProperties)`;
- `updateNametag(entityId, UpdateProperties)`;
- `refreshText(uuid, delay)`;
- `rebuildOwner(player, delay, refreshText)`;
- `refreshViewer(player)`;
- `removeNametag` / `removeAllNametags`;
- `getRegisteredPlayers`.

`UpdateProperties` can request text refresh, forced rebuild, owner-only reconciliation or normal owner+viewer reconciliation with delay.

All manager public mutations route to main thread through feature task manager when needed.

## Messages

| Key | Variables | Use |
|---|---|---|
| `nametags.prefix` | localization/PAPI context | First text component. |
| `nametags.playername` | localization/PAPI context | Name component. |
| `nametags.suffix` | localization/PAPI context | Last component. |
| `nametags.selfview.enabled` | none | Preference changed on. |
| `nametags.selfview.disabled` | none | Preference changed off. |
| `nametags.selfview.already_enabled` | none | Cached true. |
| `nametags.selfview.already_disabled` | none | Cached false. |
| `nametags.selfview.status_on` | none | Status. |
| `nametags.selfview.status_off` | none | Status. |
| `nametags.selfview.usage` | none | Subtree usage. |

## Persistence and messaging summary

- MySQL: `nametagsOrmConnection`, policy `player_data_rw`.
- Table: `player_nametags`.
- Identity: DataRegistry canonical player ID.
- Packet transport: PacketEvents + shared PacketManager.
- Redis/proxy messaging: none.
- PAPI expansion: none; localization placeholders only.
- World entities: none; all fake client-side.

## Disable and shutdown

Disable order:

1. `NametagManager.removeAllNametags` routes shutdown to main thread;
2. cancels transition tasks;
3. unregisters each logical nametag/attachment;
4. sends fake destroy IDs to all online players;
5. clears viewer/transition/preference/session state;
6. unregister PacketEvents passenger listener;
7. close static PlaceholderHook singleton;
8. framework lifecycle unregisters Bukkit listener/command/tasks/data scope.

Calling shutdown from non-main thread schedules it rather than blocking; lifecycle order must keep task manager available long enough for cleanup.

## Performance characteristics

- Track/untrack is pair-local.
- Full reconciliation every10 ticks by default traverses all logical owners, tracked viewers and stale states.
- Remount repair every100 ticks sends passenger packets for all visible pairs.
- Text refresh sends metadata only to currently visible viewers.
- Manager uses concurrent maps for async DB/session flags and main-thread maps/sets for transitions.

Network/CPU grows with visible player pairs (roughly O(n²) in dense player groups). Tune distance/reconciliation/repair carefully but do not remove repair without testing tracking edge cases.

## Developer source map

- Integration/defaults/shutdown: `features/nametags/Nametags.java`
- Lifecycle/state machine: `features/nametags/internal/NametagManager.java`
- Logical fake entity: `features/nametags/internal/Nametag.java`
- Per-viewer state: `features/nametags/internal/NametagViewerState.java`
- Registry: `features/nametags/internal/NametagRegistry.java`
- Packet emission: `features/nametags/internal/update/NametagUpdater.java`
- Attachment index/packet guard: `features/nametags/internal/packet/`
- Visibility rules: `features/nametags/internal/visibility/`
- Bukkit/Paper events: `features/nametags/listener/NametagListener.java`
- Persistence: `features/nametags/internal/NametagDBService.java`, `entities/PlayerNametagEntity.java`
- Command: `features/nametags/command/NametagCommand.java`
- Text hook: `features/nametags/internal/hook/PlaceholderHook.java`
- Tests: `src/test/.../features/nametags/`

## Operational verification

1. Test cold join/relog with self-view DB row true/false/missing and slow DataRegistry/database.
2. Toggle self-view while DB read is pending and verify stale completion cannot overwrite.
3. Walk/fly rapidly across tracking/distance boundaries with multiple viewers.
4. Teleport below/at/above configured threshold, same/cross world, cancelled teleports, portals and respawn.
5. Die/respawn, switch spectator, vanish, disguise, sit/GSit, mount/dismount, glide and change skin.
6. Accept resource pack after tags already visible.
7. Force late untrack/retrack and packet `SET_PASSENGERS` replacements from other plugins.
8. Test real passengers plus fake tag ordering.
9. Disable/reload with pending joins/transitions/spawns and inspect client ghost cleanup.
10. Test missing/failing LibsDisguises/vanish integration; visibility must fail closed.
11. Change placeholders and invoke text refresh hooks; confirm no implicit periodic refresh.
12. Load-test dense player groups while measuring reconciliation/remount packet volume.

## Troubleshooting

- **Floating/detached tag:** inspect owner passenger packets, attachment index, transition generation and remount-repair logs; test external mount/entity packet plugins.
- **Ghost after teleport/relog:** verify destroy packet and entity-generation rotation, settle delays and lifecycle task cancellation.
- **Tag never appears:** check Paper tracking, same world/distance, visibility rules, owner suspension, PacketEvents and delayed spawn logs.
- **Self-view disappears while gliding:** intentional temporary suppression.
- **Prefix/name stays stale:** no periodic text refresh; trigger/update hook or add refresh scheduling.
- **Disguised/vanished tags leak:** condition failures fail closed; verify `viewer.canSee`, LibsDisguises startup state and packet plugins.
- **Self-view not saved:** inspect DataRegistry persisted identity and MySQL `player_nametags`; runtime preference is not rolled back on failure.
- **High packet/CPU load:** visible-pair reconciliation/remount scales quadratically; tune distance/intervals after in-game reliability testing.
