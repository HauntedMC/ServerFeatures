# WorldEditVisualizer

> Paper · Feature ID `worldeditvisualizer` · disabled by default · commands `/worldeditvisualizer` and `/wevis`

WorldEditVisualizer renders a player's current WorldEdit cuboid selection with **packet-only virtual `BlockDisplay` and `TextDisplay` entities**. The displays exist solely in the client of the player using the visualizer. They are never added to a Bukkit world, never tick, never enter a chunk, never save to disk, and cannot remain behind as server entities.

This replaces the former implementation, which spawned real display entities and relied on every lifecycle path successfully retaining and removing every handle. The generic world-display API that enabled that implementation has been removed from `serverfeatures-api` because no other feature used it.

## Safety model

The renderer has the following guarantees:

1. **No world entities.** Spawn, metadata, and destroy packets are sent directly to one player through PacketEvents.
2. **No world mutation.** No real or fake block state is changed, so visualization cannot affect blocks, block entities, movement prediction, collision, or interaction.
3. **Per-player isolation.** Nearby players receive no packets and cannot see or interact with another player's selection.
4. **Bounded output.** Distance clipping and a hard per-player entity budget prevent large selections from creating unbounded work or client load.
5. **No chunk loading.** Selection geometry is calculated from coordinates only; the renderer never requests block data or loads chunks.
6. **Deterministic cleanup.** Every active virtual entity ID is tracked per viewer and destroyed on disable, selection invalidation, teleport, world change, respawn, permission loss, reload, or a guarded failure.
7. **Client-state self-healing.** A bounded periodic full rebuild repairs virtual displays if the client silently loses packet-only entities.

Paper's `World#createEntity` is used only to construct short-lived, **unspawned** display templates. Their metadata is converted to PacketEvents data once and cached. The templates are never passed to `addEntity` or any spawn method.

## Dependency and activation

The feature depends on `FastAsyncWorldEdit` and uses the WorldEdit API exposed by it. It supports complete `CuboidRegion` selections. Other selector types are deliberately rejected rather than rendered incorrectly.

On initialization the feature:

1. creates one visualization service and packet renderer;
2. registers the command and player lifecycle listener;
3. optionally enables already-online permitted players;
4. starts one synchronous selection reconciliation task.

WorldEdit session reads, Bukkit template construction, and packet sends all remain on the server thread.

## Command and permission

Permission: `serverfeatures.feature.worldeditvisualizer.use`

| Syntax | Behavior |
|---|---|
| `/wevis` | Toggle the visualizer. |
| `/wevis toggle` | Toggle the visualizer. |
| `/wevis on` | Enable and immediately read the current selection. |
| `/wevis off` | Disable and destroy all virtual displays for the player. |
| `/wevis refresh` | Force a complete packet rebuild; enables first when necessary. |

Aliases `enable` and `disable` are accepted in command execution. Tab completion exposes `toggle`, `on`, `off`, and `refresh`.

Enabled state is session-local and is not written to the database. When `auto_enable_on_join` is enabled, every joining player with the permission is enabled again. Permission revocation while online is detected by polling and immediately destroys the viewer's active displays.

## Configuration

```yaml
enabled: false
auto_enable_on_join: true

edge:
  material: WHITE_STAINED_GLASS
  step_blocks: 0.25
  scale: 0.15

corner:
  material: LIME_STAINED_GLASS
  pos1_material: BLUE_STAINED_GLASS
  pos2_material: RED_STAINED_GLASS
  scale: 1.0

glow:
  edge_color: aqua
  corner_color: lime
  pos1_color: blue
  pos2_color: red

label:
  enabled: true
  show_prefix_hash: true
  scale: 0.8
  y_offset: 0.8

render:
  max_distance_blocks: 128
  max_entities: 1024
  movement_refresh_blocks: 8
  full_refresh_interval_ticks: 600

poll:
  interval_ticks: 10
```

### Materials and scale

| Key | Default | Runtime behavior |
|---|---|---|
| `edge.material` | `WHITE_STAINED_GLASS` | Block data shown by virtual edge displays. |
| `edge.step_blocks` | `0.25` | Requested sub-block interval between edge displays; minimum runtime value `0.05`. |
| `edge.scale` | `0.15` | Uniform edge-display scale, clamped to `0.01–8.0`. |
| `corner.material` | `LIME_STAINED_GLASS` | Ordinary cuboid corner material. |
| `corner.pos1_material` | `BLUE_STAINED_GLASS` | WorldEdit position 1 material. |
| `corner.pos2_material` | `RED_STAINED_GLASS` | WorldEdit position 2 material. |
| `corner.scale` | `1.0` | Corner/position display scale, clamped to `0.01–8.0`. |

Materials are resolved with `Material.matchMaterial`. Invalid or non-block materials fall back to the documented defaults. Pos1 and pos2 styling override an ordinary corner when their coordinates overlap; pos2 wins when both positions are identical.

### Glow colors

The four `glow.*` keys configure Bukkit display glow overrides. Common Minecraft names such as `aqua`, `blue`, `red`, `lime`, `gold`, and `dark_aqua` are supported, as are six-digit RGB values such as `#35d7ff`. Invalid values use the corresponding default.

### Labels

| Key | Default | Behavior |
|---|---:|---|
| `label.enabled` | `true` | Spawn virtual text labels above pos1 and pos2. |
| `label.show_prefix_hash` | `true` | Use `#1` / `#2`; when false use `pos1` / `pos2`. |
| `label.scale` | `0.8` | Uniform text-display scale, clamped to `0.1–4.0`. |
| `label.y_offset` | `0.8` | Vertical offset from the block-center marker. |

Labels are centered, billboarded, see-through, shadowed, and rendered with a translucent background. They count toward the same per-player entity budget as block displays.

### Rendering budgets and refresh

| Key | Default | Runtime bounds | Behavior |
|---|---:|---:|---|
| `render.max_distance_blocks` | `128` | `1–512` | Axis-aligned distance around the viewer in which virtual displays may exist. |
| `render.max_entities` | `1024` | `16–4096` | Hard maximum number of virtual displays owned by one viewer. |
| `render.movement_refresh_blocks` | `8` | `1–32` | Size of movement cells used to recalculate distance-clipped geometry. |
| `render.full_refresh_interval_ticks` | `600` | `0–72000` | Periodically destroy and rebuild the bounded client-only display set. `0` disables periodic full rebuilding. |

The sampler first clips each of the twelve cuboid edges to the viewer's configured range. It never walks across the invisible middle of an enormous selection. If the requested `edge.step_blocks` would exceed the remaining entity budget, the effective step is increased adaptively. Corners, pos1, pos2, and labels are prioritized before ordinary edge points. A deterministic final cap remains as a defensive backstop.

Coordinates are quantized to 1/4096 of a block for stable identity and packet diffing. This preserves fractional spacing without accumulating floating-point duplicates.

The periodic full refresh is a resilience mechanism for client-only entities. Normal movement and selection updates remain differential; only the configured interval performs a complete destroy-and-recreate cycle. The default is 600 ticks (30 seconds), which bounds recovery time without rebuilding every poll.

### Polling

`poll.interval_ticks` defaults to `10` and is clamped to at least one tick. Each poll checks only enabled players. An unchanged selection is skipped until the player enters a new movement cell, the selection changes, the full-refresh deadline becomes due, or `/wevis refresh` forces a rebuild.

## Packet lifecycle

For each enabled player, reconciliation performs the following:

1. verify that the player remains online and permitted;
2. read the existing WorldEdit session without creating one;
3. read the selection in the player's current world;
4. require a complete cuboid selection;
5. compare the selection and movement-cell fingerprint;
6. decide whether differential reconciliation or a due full rebuild is required;
7. calculate the bounded desired virtual display set;
8. send one destroy packet for virtual IDs no longer required, or for the entire previous set during a full rebuild;
9. retain unchanged virtual displays during differential reconciliation;
10. send spawn and metadata packets only for newly required displays.

`/wevis refresh` deliberately destroys and recreates the full set. Normal polling uses a diff so movement and point changes do not respawn the complete visualization.

If the selection is cleared, becomes incomplete, switches to an unsupported selector, or cannot be read in the current world, all previous virtual displays are destroyed immediately.

## Player lifecycle

- **Join:** optionally enable and render when permitted.
- **Quit:** forget in-memory state; the disconnected client no longer exists and no server entity can remain.
- **Teleport:** destroy the old packet entities before a successful teleport is applied.
- **World change:** best-effort destroy any tracked IDs, discard old-dimension state, and immediately build the destination-world selection.
- **Respawn:** best-effort destroy tracked IDs and invalidate the client-side state; the next poll recreates the visualization.
- **Permission loss:** disable and destroy the player's virtual entities.
- **Feature disable/reload:** destroy all active virtual entities for online viewers and clear all state maps.

A runtime failure is isolated to the affected viewer, logged, and followed by best-effort destruction and session disablement. This prevents one bad selection or client state from aborting the poll for other viewers or producing a repeating exception loop.

## Performance characteristics

The server stores a bounded map of virtual display keys and IDs per enabled player. There are no ticking visualization entities, chunk entity lists, persistence records, block reads, or block-update packets.

Changed-render work is bounded by:

- twelve distance-clipped edge ranges;
- at most `render.max_entities` desired keys;
- one batched destroy packet for removed IDs;
- two packets per newly spawned virtual display: spawn and metadata.

A due full refresh performs the same bounded work for the complete current set. Operators with many simultaneous visualizer users can increase `render.full_refresh_interval_ticks`, reduce `render.max_entities`, or set the interval to `0` when periodic client-state repair is not desired.

The metadata for the six display styles is generated once from unspawned templates and reused for all viewers until feature reload.

## Removed legacy API

The unused package below has been removed from `serverfeatures-api`:

```text
nl.hauntedmc.serverfeatures.api.ui.world.display
```

Removed types include `VisualHandle`, `Visualisation`, `DisplayVisualHandle`, `VisualOptions`, `RegionShape`, `CuboidRegionShape`, and `CubeRegionVisualisation`, together with their tests. The API module's now-unused direct JOML dependency was also removed.

This is an intentional breaking cleanup. External code that imported these classes must stop using them; ServerFeatures no longer exposes a generic world-entity visualization API.

The feature's existing material, spacing, scale, glow, and label settings remain supported by the packet-only renderer.

## Messages

| Key | Use |
|---|---|
| `worldeditvisualizer.enabled` | Enabled confirmation. |
| `worldeditvisualizer.disabled` | Disabled and virtual entities destroyed. |
| `worldeditvisualizer.refreshed` | Forced packet rebuild succeeded. |
| `worldeditvisualizer.no_selection` | No complete selection exists in the current world. |
| `worldeditvisualizer.not_cuboid` | Current selector type is unsupported. |
| `worldeditvisualizer.failed` | Guarded packet/render update failed and details were logged. |
| `worldeditvisualizer.usage` | Invalid command argument. |

## Verification checklist

1. Enable with no selection, an incomplete pos1-only selection, a complete cuboid, and a non-cuboid selector.
2. Change, clear, and switch selector type; verify old virtual displays disappear immediately.
3. Stand another player beside the selection and confirm they never see the visualization.
4. Inspect Bukkit entities, chunk entity counts, entity-save data, and timings before/during/after use; the visualization must create none.
5. Walk, sprint, fly, and pass directly through every visual point; there must be no collision, rubber-banding, or blocked interaction.
6. Cross movement cells and chunk/vertical-section boundaries while changing both WorldEdit points.
7. Teleport within a world, change worlds, respawn, disconnect, and reconnect.
8. Revoke permission while the visual is active.
9. Disable/reload ServerFeatures with several active viewers.
10. Test tiny, flat, line-like, very large, and extreme-coordinate selections.
11. Configure very small spacing with a low entity budget; verify adaptive thinning and stable TPS/client FPS.
12. Test invalid materials, colors, scales, distances, budgets, and refresh intervals; safe fallbacks and clamps must apply.
13. Toggle labels and both label naming modes.
14. Run `/wevis refresh` repeatedly and verify entity IDs are replaced without duplicate visuals.
15. Leave an unchanged visualization active beyond `render.full_refresh_interval_ticks`; verify it rebuilds once without leaving duplicates or world entities.

## Source map

- Defaults and lifecycle: `features/worldeditvisualizer/WorldEditVisualizer.java`
- Command: `features/worldeditvisualizer/command/WorldEditVisualizerCommand.java`
- Selection state and reconciliation: `features/worldeditvisualizer/internal/VisualizationService.java`
- Packet-only virtual displays: `features/worldeditvisualizer/internal/PacketDisplayRenderer.java`
- Bounded sub-block geometry: `features/worldeditvisualizer/internal/CuboidOutlineSampler.java`
- Player lifecycle: `features/worldeditvisualizer/listener/PlayerLifecycleListener.java`
- Geometry tests: `features/worldeditvisualizer/internal/CuboidOutlineSamplerTest.java`
