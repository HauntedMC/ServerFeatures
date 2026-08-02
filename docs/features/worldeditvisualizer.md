# WorldEditVisualizer

> Paper · Feature ID `worldeditvisualizer` · disabled by default · commands `/worldeditvisualizer` and `/wevis`

WorldEditVisualizer renders a player's current WorldEdit cuboid selection as **client-only fake block changes**. It creates no Bukkit entities, display entities, armor stands, persistent records, or real blocks. Only the player who enabled the visualizer receives the packets.

This replaces the former display-entity implementation. The old generic `serverfeatures-api` world-display API and its tests have been removed because no other feature used it and its server-side entity ownership made leaked visuals possible.

## Safety model

The renderer has four hard guarantees:

1. **No world mutation.** It uses Paper's per-player multi-block-change API only.
2. **No server entities.** There is nothing that can remain in a world, tick, save into chunks, or be enumerated by entity-management plugins.
3. **Bounded output.** Distance and block-count limits prevent large WorldEdit selections from producing unbounded work.
4. **Authoritative restoration.** When a point leaves the visualization, the current live block data is read and sent back to the player. Tile-entity data is re-sent after the block packet for chests, signs, spawners, and other `TileState` blocks. The renderer never restores an old cached block snapshot over a newer real block change.

Only loaded chunks are read or visualized. Rendering does not load chunks.

## Dependency and activation

The feature depends on `FastAsyncWorldEdit` and uses the WorldEdit API exposed by it. It supports complete `CuboidRegion` selections. Other selectors are deliberately rejected.

On initialization the feature:

1. creates one packet renderer and visualization service;
2. registers the command and player lifecycle listener;
3. optionally enables already-online permitted players;
4. starts one synchronous selection poll task.

All WorldEdit session reads, Bukkit world reads, and packet sends stay on the server thread.

## Command and permission

Permission: `serverfeatures.feature.worldeditvisualizer.use`

| Syntax | Behavior |
|---|---|
| `/wevis` | Toggle the visualizer. |
| `/wevis toggle` | Toggle the visualizer. |
| `/wevis on` | Enable and immediately read the current selection. |
| `/wevis off` | Disable and restore every currently faked block. |
| `/wevis refresh` | Force an immediate selection read and packet refresh; enables first when needed. |

Aliases `enable` and `disable` are accepted in command execution, while tab completion exposes the shorter `on` and `off` forms.

Enabling is a player session preference, not persisted state. When `auto_enable_on_join` is enabled, every joining player with the permission is enabled again. Revoking the permission while online is detected by polling; the visual is restored and the player is disabled.

## Configuration

```yaml
enabled: false
auto_enable_on_join: true

edge:
  material: WHITE_STAINED_GLASS
  step_blocks: 1

corner:
  material: LIME_STAINED_GLASS
  pos1_material: BLUE_STAINED_GLASS
  pos2_material: RED_STAINED_GLASS

render:
  max_distance_blocks: 128
  max_blocks: 2048
  refresh_interval_ticks: 100

poll:
  interval_ticks: 10
```

### Materials

| Key | Default | Use |
|---|---|---|
| `edge.material` | `WHITE_STAINED_GLASS` | Sampled cuboid edges. |
| `corner.material` | `LIME_STAINED_GLASS` | Ordinary cuboid corners. |
| `corner.pos1_material` | `BLUE_STAINED_GLASS` | WorldEdit position 1. |
| `corner.pos2_material` | `RED_STAINED_GLASS` | WorldEdit position 2. |

Values are resolved with `Material.matchMaterial`. A missing, invalid, or non-block material falls back to the default. Pos1 and pos2 override ordinary-corner styling when coordinates overlap; pos2 wins when both positions are identical.

### Geometry and budgets

| Key | Default | Runtime bounds | Behavior |
|---|---:|---:|---|
| `edge.step_blocks` | `1` | minimum `1` | Requested interval between edge points. |
| `render.max_distance_blocks` | `128` | `1–512` | Axis-aligned distance around the player considered for rendering. |
| `render.max_blocks` | `2048` | `16–8192` | Maximum fake blocks owned by one player's visualization. |

The sampler clips each of the twelve cuboid edges to the player's visible distance before iterating. It therefore does not walk from one end of a million-block selection to the other. When the requested step would exceed the per-player block budget, the sampler increases the effective step. A final deterministic cap is retained as a defensive backstop.

Corners and WorldEdit position markers are prioritized before ordinary edge points. Points outside world height or in unloaded chunks are skipped.

### Polling and refresh

| Key | Default | Behavior |
|---|---:|---|
| `poll.interval_ticks` | `10` | Selection/permission reconciliation interval; clamped to at least one tick. |
| `render.refresh_interval_ticks` | `100` | Re-send unchanged visuals periodically. `0` disables periodic re-sends. |

A render fingerprint contains the complete selection plus the player's current chunk and vertical chunk section. Selection changes and chunk/section movement refresh immediately on the next poll. The periodic refresh repairs client state after chunk re-sends or other packets overwrite fake blocks without requiring entity respawns.

## Selection lifecycle

For every enabled player, each poll:

1. verifies the player is online and still has permission;
2. reads the existing WorldEdit session without creating one;
3. reads the selection in the player's current world;
4. requires a complete cuboid selection;
5. compares the selection and coarse player position with the last fingerprint;
6. computes a bounded set of currently visible fake blocks;
7. restores points removed since the previous render from live world block data;
8. re-sends `TileState` data for restored block entities after the block packet;
9. sends the new fake block map only to that player.

If the selection is cleared, becomes incomplete, changes to an unsupported selector, or cannot be read, the previous visual is immediately restored. This fixes the former stale-selection behavior.

## Player lifecycle

- **Join:** optionally auto-enable when permitted.
- **Quit:** forget all in-memory state; no cleanup entity can remain because none exists.
- **Teleport:** restore the old-world/current-location visual before the successful teleport is applied.
- **World change:** invalidate old state and immediately read/render the destination-world selection.
- **Respawn:** invalidate the fingerprint; the poll recreates the visual for the new client state.
- **Feature disable/reload:** restore all online viewers, clear every state map, then let lifecycle management cancel tasks/listeners.

Packet sending and cleanup failures are isolated per player and logged. An unexpected update failure disables that player's visualization session after a best-effort restoration, preventing a poll-time exception loop from spamming logs. One broken selection or client cannot abort updates for other viewers.

## Performance characteristics

The server owns at most a small set/map of coordinates and materials per enabled player. There are no ticking entities and no per-selection persistence.

Work per changed render is bounded by:

- at most twelve distance-clipped edge ranges;
- at most `render.max_blocks` desired coordinates;
- restoration of at most the previous render's bounded coordinates;
- Paper grouping fake block changes into the necessary chunk-section packets.

Unchanged selections are skipped until the player crosses a chunk/section boundary or the periodic refresh becomes due.

## Removed legacy surface

The following unused API package was removed:

```text
nl.hauntedmc.serverfeatures.api.ui.world.display
```

This includes `VisualHandle`, `Visualisation`, `DisplayVisualHandle`, `VisualOptions`, `RegionShape`, `CuboidRegionShape`, and `CubeRegionVisualisation`, plus their tests. External code that imported these classes must stop doing so; ServerFeatures no longer exposes a generic server-entity visualization API.

The obsolete configuration keys below are no longer read and may be deleted from existing feature configs:

```text
glow.*
edge.scale
corner.scale
label.*
```

## Messages

| Key | Use |
|---|---|
| `worldeditvisualizer.enabled` | Enabled state confirmation. |
| `worldeditvisualizer.disabled` | Disabled and restored confirmation. |
| `worldeditvisualizer.refreshed` | Forced refresh succeeded. |
| `worldeditvisualizer.no_selection` | No complete selection exists in the current world. |
| `worldeditvisualizer.not_cuboid` | Current selection type is unsupported. |
| `worldeditvisualizer.failed` | A guarded render/update failed and details were logged. |
| `worldeditvisualizer.usage` | Invalid command argument. |

## Verification checklist

1. Enable with no selection, incomplete pos1-only selection, complete cuboid, and a non-cuboid selector.
2. Change, clear, and switch selector type; confirm the old fake blocks restore immediately.
3. Verify another nearby player never sees the visualization.
4. Inspect entities and chunk saves before, during, and after visualization; no visualization entity/block should exist.
5. Walk and fly across chunk and vertical-section boundaries while changing both selection points.
6. Teleport within the world, between worlds, respawn, disconnect, and reconnect.
7. Revoke permission while the visual is active.
8. Disable/reload ServerFeatures with active viewers.
9. Test a very large and very long selection; packet count and server work must remain bounded.
10. Test selections partly outside world height and through unloaded chunks.
11. Change a real block beneath a fake point, then clear/refresh; the latest real block must be shown.
12. Overlay a chest, sign, spawner, and other block entity, then clear; its client-side contents/text/state must restore correctly.
13. Test invalid/non-block material values and extreme budget/distance values; safe fallbacks/clamps must apply.

## Source map

- Defaults and lifecycle: `features/worldeditvisualizer/WorldEditVisualizer.java`
- Command: `features/worldeditvisualizer/command/WorldEditVisualizerCommand.java`
- Selection state and reconciliation: `features/worldeditvisualizer/internal/VisualizationService.java`
- Packet-only fake-block rendering: `features/worldeditvisualizer/internal/PacketCuboidRenderer.java`
- Bounded edge geometry: `features/worldeditvisualizer/internal/CuboidOutlineSampler.java`
- Player lifecycle: `features/worldeditvisualizer/listener/PlayerLifecycleListener.java`
- Geometry tests: `features/worldeditvisualizer/internal/CuboidOutlineSamplerTest.java`
