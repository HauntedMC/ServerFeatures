# WorldEditVisualizer

> Paper · Feature ID `worldeditvisualizer` · disabled by default · commands `/worldeditvisualizer` and `/wevis`

WorldEditVisualizer polls each enabled player's current WorldEdit cuboid selection and renders it with real Bukkit `BlockDisplay` and `TextDisplay` entities. Those entities are hidden by default and explicitly shown only to the owning viewer.

The visual contains:

- specially styled pos1 and pos2 corner cubes;
- optional pos1/pos2 labels;
- the remaining cuboid corner cubes;
- sampled cubes along all cuboid edges.

It does not use particles, client-side block changes or WorldEdit CUI packets.

## Dependency and activation

The implementation directly links to WorldEdit classes and calls `WorldEdit.getInstance()`. There is no runtime availability guard in the feature. A compatible WorldEdit API must therefore be present when the feature class is loaded/initialized.

On initialization:

1. create one `VisualizationService`;
2. register the toggle command;
3. register join/quit listeners;
4. automatically enable every already-online player with the use permission;
5. start the polling task when `poll.interval_ticks > 0`.

Every permitted player is also automatically enabled on every join. A manual disabled choice is not persisted across reconnects.

## Command and permission

| Syntax | Permission | Behavior |
|---|---|---|
| `/worldeditvisualizer` | `serverfeatures.feature.worldeditvisualizer.use` | Toggle current state. Player-only. |
| `/wevis` | same | Alias. |
| `/wevis toggle` | same | Also toggles. |

The command ignores all arguments and always toggles. `/wevis anything` therefore behaves exactly like `/wevis toggle`.

Console/non-player senders receive no message and return successfully.

Command metadata declares the permission, and execution checks it again. Tab completion always returns `toggle` without prefix filtering.

## Configuration

### Display materials

| Key | Default | Use |
|---|---|---|
| `edge.material` | `WHITE_STAINED_GLASS` | Edge sample cubes. |
| `corner.material` | `LIME_STAINED_GLASS` | Unnamed cuboid corners. |
| `corner.pos1_material` | `BLUE_STAINED_GLASS` | WorldEdit pos1 cube. |
| `corner.pos2_material` | `RED_STAINED_GLASS` | WorldEdit pos2 cube. |

Materials are resolved through `Material.matchMaterial`. Invalid values silently use the listed fallback. The implementation does not restrict values to blocks before calling `Bukkit.createBlockData`; a non-block material can fail at render time.

### Glow colors

| Key | Default |
|---|---|
| `glow.edge_color` | `aqua` |
| `glow.corner_color` | `aqua` |
| `glow.pos1_color` | `blue` |
| `glow.pos2_color` | `red` |

Values use `NamedTextColor.NAMES.value`. Unknown values silently use the corresponding default. The service does not normalize case before lookup.

Every display is full-bright, glowing and receives a Bukkit RGB glow override derived from the named color.

### Geometry

| Key | Default | Behavior |
|---|---:|---|
| `edge.step_blocks` | `0.25` | Distance between sampled edge cubes. Clamped to at least `0.25`. |
| `edge.scale` | `0.15` | Uniform scale of each edge cube. Not clamped. |
| `corner.scale` | `1.0` | Uniform scale of all corner cubes. Not clamped. |

The runtime fallback inside `buildOptionsFromConfig` differs slightly from declared defaults (`0.5`, `0.18`, `0.45`), but declared config keys normally exist. If a key is absent or unparsable, the internal fallback is used.

There is no maximum selection size, entity count, edge-point count, view distance or per-render budget.

### Labels

| Key | Default | Behavior |
|---|---:|---|
| `label.enabled` | `true` | Spawn labels for named points. |
| `label.y_offset` | `0.7` | Vertical offset above the point center. |
| `label.scale` | `1.0` | Uniform TextDisplay scale. |
| `label.show_prefix_hash` | `false` | `false`: `pos1`/`pos2`; `true`: `#1`/`#2`. |

Labels are center-billboarded, see-through, shadowed, glowing, line width 120 and use a semi-transparent black background.

### Polling

| Key | Default | Behavior |
|---|---:|---|
| `poll.interval_ticks` | `10` | Period for selection polling. Values `<=0` disable polling. |

Without polling, the command's initial enable attempt is the only automatic selection read. WorldEdit selection changes are not reflected until another explicit enable/toggle/reload path.

## Player state

`VisualizationService` maintains three concurrent collections:

| State | Meaning |
|---|---|
| `enabled: Set<UUID>` | Players included in polling. |
| `last: Map<UUID,SelectionSnapshot>` | Last rendered world/min/max/pos1/pos2 for diff suppression. |
| `shown: Map<UUID,VisualHandle>` | Current display-entity handle. |

`isActive` returns true when either enabled state or a visual handle exists. This lets toggle clean up an orphaned handle even if the enabled flag was lost.

### Enable

`enable(player)`:

1. clears the current handle/entities;
2. removes the last snapshot;
3. adds UUID to enabled set;
4. immediately attempts to render the current selection without feedback.

It always refreshes/cleans before rendering, even when already enabled.

### Disable

`disable(player,true)` removes enabled and last state, then clears/removes every display in the handle.

### Toggle

- active → disable and clear;
- inactive → clear any stale handle/snapshot, enable and render once.

The command reports enabled even when no usable selection was found, because `enable` represents polling state rather than successful visualization creation.

## WorldEdit selection read

For an enabled online player:

1. get an existing WorldEdit `LocalSession` with `getIfPresent`;
2. adapt the player's current Bukkit world;
3. call `session.getSelection(world)`;
4. require `CuboidRegion`;
5. read min, max, pos1 and pos2;
6. create a snapshot including Bukkit world UUID;
7. skip rendering when snapshot equals the prior snapshot;
8. build the shape/options;
9. clear previous handle;
10. create and store a new visual handle.

Only complete cuboid selections are supported. Polygonal, ellipsoid, convex and other WorldEdit region selectors are ignored.

### Stale-render behavior

When polling finds:

- no existing WorldEdit session;
- no complete selection;
- a non-cuboid selection;

`tryShowFromSelection` returns without clearing `last` or `shown`. A previous cuboid visualization therefore remains visible after the selection is cleared or changed to an unsupported type, until:

- a different complete cuboid snapshot is rendered;
- the player toggles off/on;
- the player quits and cleanup completes;
- the feature disables.

The `no_selection` and `not_cuboid` messages are sent only when the method is called with `feedback=true`. All current calls use `false`, so these messages are defined but not reached by the current command/join/poll implementation.

## Shape and coordinate mapping

The cuboid uses inclusive WorldEdit block min/max coordinates. Named point centers are:

```text
(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
```

`CuboidRegionShape` provides all corner centers and samples every edge at the configured step. Exact vector equality prevents pos1/pos2 from also being spawned as ordinary corners.

Pos1 and pos2 can coincide or correspond to the same geometric point in a one-block selection. Named entries are processed separately, so overlapping special cubes/labels may be created.

## Display entity implementation

For every cube, `CubeRegionVisualisation` spawns a real `BlockDisplay` in the player's current world:

- block data set from configured material;
- brightness 15/15;
- glow enabled and color override;
- no shadow radius;
- `visibleByDefault=false`;
- transformation translated by negative half scale to center the cube;
- shown only through `viewer.showEntity(plugin, display)`.

Labels use real `TextDisplay` entities with the options described above.

These are server entities, not packets generated solely for the client. Other systems that enumerate entities can observe them even though ordinary players cannot see them by default.

`DisplayVisualHandle.clear()` removes each live display entity exactly once and clears its list.

## Entity-count and performance model

Each render creates:

- 2 named point cubes;
- up to 2 labels;
- up to 6 remaining corner cubes;
- edge sample cubes proportional to total edge length divided by `edge.step_blocks`.

A rectangular cuboid has 12 edges. With step 0.25, an axis length of 1,000 blocks can create thousands to tens of thousands of display entities depending on dimensions and shape sampling endpoints.

There is no maximum region volume/edge length or entity cap. Polling snapshot equality prevents recreating unchanged selections, but changing one endpoint clears every old entity and respawns the entire visual.

Operators should restrict the use permission and test realistic large selections. This feature can create severe entity/tick/memory load with very large WorldEdit regions.

Polling itself runs synchronously through the normal repeating task, so WorldEdit reads, display removal and entity spawning occur on the main thread.

## Join, quit and disable ordering

Join/quit handlers use default `NORMAL` priority and process cancelled events.

### Join

A player with use permission is automatically enabled and immediately rendered when possible. Players without permission are ignored.

### Quit

The listener calls only `service.clear(player)`. It does not directly remove UUID from `enabled` or `last`.

When polling is enabled, the next poll sees the player offline and `disableOffline` removes all three states. When polling is disabled, enabled/snapshot state can remain in memory until rejoin or feature disable. A same-UUID rejoin calls `enable`, which clears stale snapshot and remains enabled.

### Disable

The feature clears visual handles for every currently online player and drops its service reference. It does not explicitly iterate offline retained UUID state, but the service object becomes unreachable after lifecycle cleanup.

The lifecycle manager owns task/listener cancellation.

## Permission changes

Permission is checked only:

- at command execution;
- when auto-enabling during initialize/join.

Polling does not re-check permission. Revoking permission while the player remains enabled does not stop visualization until they toggle, quit or the feature reloads. Granting permission while online does not auto-enable until command/rejoin/reload.

## Messages

| Key | Current use |
|---|---|
| `worldeditvisualizer.enabled` | Toggle result: enabled state, whether or not a selection rendered. |
| `worldeditvisualizer.disabled` | Toggle result: disabled/cleared. |
| `worldeditvisualizer.no_selection` | Defined but current callers never request feedback. |
| `worldeditvisualizer.not_cuboid` | Defined but current callers never request feedback. |

## Persistence and APIs

Player preference is not persisted. There is no database, Redis, PAPI or external public service registration. The visualisation API classes are reusable internal/public API utilities, but this feature's concrete service is not registered through the feature API manager.

## Important implementation boundaries

- Direct WorldEdit dependency with no availability guard.
- Players with permission auto-enable on every join.
- Command arguments are ignored.
- Polling can be disabled with interval `<=0`.
- Cleared/non-cuboid selections leave stale visual entities visible.
- No selection-size/entity-count cap exists.
- Real display entities are spawned on the main thread.
- Permission changes are not continuously reconciled.
- Quit relies on the next poll to remove enabled/snapshot state.
- Invalid materials/colors silently fall back; scales are not clamped.
- No-selection/non-cuboid messages are currently unreachable.

## Verification checklist

1. Toggle with no selection, incomplete selection, cuboid and non-cuboid selectors.
2. Clear/change an existing cuboid to verify the documented stale-render behavior.
3. Use one-block and coincident pos1/pos2 selections and inspect overlapping displays.
4. Measure display count and tick impact for long/thin and large cuboids at several step sizes.
5. Test invalid/non-block materials, color casing and negative/zero scales.
6. Disable polling and change selections; verify only the initial render remains.
7. Revoke/grant permission while online.
8. Quit with polling enabled and disabled and inspect service state/entity cleanup.
9. Disable/reload with active visuals and verify every display entity is removed.
10. Test WorldEdit reload/unavailability and unsupported API versions.
11. Inspect whether entity-management plugins affect invisible display entities.

## Source map

- Defaults, lifecycle and poll scheduling: `features/worldeditvisualizer/WorldEditVisualizer.java`
- WorldEdit bridge/state/diff/options: `features/worldeditvisualizer/internal/VisualizationService.java`
- Command/permission: `features/worldeditvisualizer/command/WorldEditVisualizerCommand.java`
- Join/quit behavior: `features/worldeditvisualizer/listener/PlayerJoinListener.java`
- Display rendering: `serverfeatures-api/.../CubeRegionVisualisation.java`
- Handle cleanup: `serverfeatures-api/.../DisplayVisualHandle.java`
