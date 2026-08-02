# WorldEditVisualizer

> Paper · Feature ID `worldeditvisualizer` · disabled by default · commands `/worldeditvisualizer` and `/wevis`

WorldEditVisualizer renders a player's current WorldEdit cuboid selection entirely through player-scoped fake block-change packets. It never spawns `BlockDisplay`, `TextDisplay`, armor stand, marker, or other Bukkit entities, and it never changes a real world block.

This design makes the visualization impossible to persist in the world or appear to another player. Disconnecting also discards all client-side fake blocks automatically.

## Dependency and activation

The feature depends on `FastAsyncWorldEdit` and uses the WorldEdit session API. Players with `serverfeatures.feature.worldeditvisualizer.use` are enabled automatically on join and may toggle the visualizer with `/wevis`.

The feature polls the current selection on the server thread. Only complete `CuboidRegion` selections are supported.

## Commands and permission

| Syntax | Permission | Behavior |
|---|---|---|
| `/worldeditvisualizer` | `serverfeatures.feature.worldeditvisualizer.use` | Toggle the visualizer. Player-only. |
| `/wevis` | same | Alias. |
| `/wevis toggle` | same | Toggle the visualizer. |

The enabled choice is session-local and is not stored in a database.

## Packet rendering model

For every enabled player, the service:

1. reads the player's current WorldEdit cuboid;
2. samples the 12 cuboid edges into a bounded set of block coordinates;
3. assigns separate fake block materials to edges, ordinary corners, pos1 and pos2;
4. sends `Player#sendBlockChange` only to that player;
5. remembers exactly which fake positions were sent;
6. restores those positions with the authoritative world block data before replacing or clearing the visualization.

No packet library or NMS access is required. Bukkit's player block-change API emits the clientbound block updates.

The client receives only fake block states. Server collision, lighting, pathfinding, saving, chunk data and other players remain unchanged.

## Configuration

| Key | Default | Behavior |
|---|---:|---|
| `edge.material` | `WHITE_STAINED_GLASS` | Fake material for sampled edge blocks. |
| `corner.material` | `LIME_STAINED_GLASS` | Fake material for ordinary cuboid corners. |
| `corner.pos1_material` | `BLUE_STAINED_GLASS` | Fake material for WorldEdit pos1. |
| `corner.pos2_material` | `RED_STAINED_GLASS` | Fake material for WorldEdit pos2. |
| `edge.step_blocks` | `1` | Requested integer sampling distance along each edge. Clamped to at least 1. |
| `render.max_blocks` | `2048` | Hard per-player fake-block budget. Clamped to at least 8. |
| `render.max_distance_blocks` | `192` | Maximum 3D distance from the player for packets. Clamped to at least 16. |
| `render.resend_interval_ticks` | `100` | Periodic resend so a client chunk reload or movement cannot permanently remove a still-active visual. |
| `poll.interval_ticks` | `10` | Selection and permission reconciliation interval. Clamped to at least 1. |

Materials are resolved with `Material.matchMaterial`. Unknown or non-block materials use the documented fallback.

### Bounded large selections

The sampler always includes both endpoints of every edge. When the requested step would exceed `render.max_blocks`, it increases the effective step until the generated wireframe fits the budget. This makes very large selections deterministic and bounded instead of creating thousands of server entities.

Only coordinates within `render.max_distance_blocks` and currently loaded chunks are sent. The periodic resend recomputes this view as the player moves.

## Cleanup guarantees

The current fake positions are restored when:

- the cuboid changes;
- the selection becomes incomplete or is cleared;
- the selector changes to a non-cuboid type;
- the player toggles the feature off;
- the use permission is revoked while online;
- the player changes world or respawns;
- the feature is disabled or reloaded.

On quit, the per-player state is discarded. No restoration packets are necessary because the client connection and its fake block state no longer exist.

Restoration reads the current real `BlockData`, not a stale snapshot. A real block changed while the visualization was visible is therefore restored to its latest authoritative state.

Unloaded chunks are never force-loaded for rendering or cleanup. A client chunk unload already discards its fake block changes, while loaded positions are explicitly restored.

## State and concurrency

The service keeps:

- a concurrent enabled UUID set;
- one current immutable selection/render snapshot per player;
- the exact bounded set of fake coordinates last sent to each player.

All Bukkit and WorldEdit interaction occurs from the feature's synchronous polling/lifecycle paths. Concurrent collections protect lifecycle state without moving Bukkit world access off-thread.

Snapshot equality suppresses unnecessary packets. An unchanged visualization is resent only at `render.resend_interval_ticks` to recover from client chunk refreshes and update distance filtering.

## Removed legacy API

The old generic display-entity visualization API was only used by this feature and has been removed, including:

- `Visualisation` and `VisualHandle`;
- `DisplayVisualHandle`;
- `VisualOptions`;
- `RegionShape` and `CuboidRegionShape`;
- `CubeRegionVisualisation`;
- their obsolete unit tests.

This prevents future feature code from accidentally reintroducing real visualization entities through the abandoned API.

## Messages

| Key | Use |
|---|---|
| `worldeditvisualizer.enabled` | Visualizer enabled. |
| `worldeditvisualizer.disabled` | Visualizer disabled and restored. |
| `worldeditvisualizer.no_selection` | Enable attempted without a complete selection. |
| `worldeditvisualizer.not_cuboid` | Current selection is not cuboid. |

## Verification checklist

1. Create, resize and clear a cuboid and verify old fake blocks restore immediately.
2. Toggle off and inspect the same positions from another player; no visualization should ever be visible to them.
3. Select real containers, redstone and interactive blocks; verify the server block state and behavior never changes.
4. Test a one-block selection, flat selections and long thin selections.
5. Test a selection spanning tens of thousands of blocks and confirm the packet count remains within `render.max_blocks`.
6. Walk through the selection and verify distance-filtered edges appear on periodic resend.
7. Teleport, change world, respawn, disconnect, revoke permission, disable and reload the feature.
8. Inspect the world entity count before and after visualization; it must remain unchanged.
9. Restart after active visualizations; there must be no entities or blocks to clean from disk.

## Source map

- Defaults, messages and lifecycle: `features/worldeditvisualizer/WorldEditVisualizer.java`
- Packet renderer, bounded sampler and state: `features/worldeditvisualizer/internal/VisualizationService.java`
- Command: `features/worldeditvisualizer/command/WorldEditVisualizerCommand.java`
- Player lifecycle cleanup: `features/worldeditvisualizer/listener/PlayerJoinListener.java`
- Sampler tests: `serverfeatures-platform-paper/src/test/.../VisualizationServiceTest.java`
