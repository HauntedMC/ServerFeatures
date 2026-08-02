# WorldEditVisualizer

> Paper · Feature ID `worldeditvisualizer` · disabled by default · commands `/worldeditvisualizer` and `/wevis`

WorldEditVisualizer renders the owning player's current WorldEdit cuboid as a client-only display-entity wireframe. It does not spawn a Bukkit entity, add an entity to a chunk, alter a real block, or broadcast anything to another player.

The feature creates unspawned `BlockDisplay` and `TextDisplay` metadata through Paper, converts that metadata through PacketEvents, and sends only spawn/metadata packets to the viewer. Cleanup sends a destroy-entities packet for the exact client entity IDs belonging to that viewer.

This removes the failure mode of the old implementation: there is no server entity to save, tick, enumerate, leak, or leave behind in the world.

## Visual model

A complete cuboid uses a constant amount of client entities:

- 12 stretched block displays for the outer wireframe edges;
- 8 small block displays for the outer corners;
- 1 or 2 markers for WorldEdit pos1 and pos2;
- 1 or 2 optional text labels.

The absolute maximum is therefore 24 entities per enabled viewer, regardless of whether the selection is one block or millions of blocks long. Large selections no longer generate sampled displays proportional to edge length.

The wireframe encloses the complete inclusive WorldEdit block selection. For a selection from `(0, 0, 0)` through `(2, 2, 2)`, its outer bounds are `(0, 0, 0)` through `(3, 3, 3)`. Pos1 and pos2 markers remain centered in their selected blocks.

A one-block selection still has a visible one-block outer wireframe. When pos1 and pos2 are equal, one marker and one combined label are used to prevent z-fighting.

## Isolation and world safety

The renderer calls Paper's unspawned-entity creation API only to obtain version-correct display metadata. It never calls `World#addEntity`, `World#spawn`, or an entity `spawnAt` method.

Consequences:

- the visualization never enters a Bukkit world or chunk entity list;
- it cannot be written into chunk or world data;
- it cannot be found or modified by entity-management plugins;
- it has no server tick cost after packets are sent;
- only the requesting player receives it;
- fake solid blocks are not placed in the client's chunk data, so movement prediction and real block appearance remain untouched.

The client entity IDs are allocated from unspawned Paper entity instances, preventing collisions with real server entity IDs. The temporary Bukkit objects are not retained after their metadata has been converted.

## Dependency and activation

The feature depends on `FastAsyncWorldEdit` and uses the WorldEdit session API. PacketEvents is already a provided runtime dependency of ServerFeatures.

On initialization:

1. create the visualization service and packet renderer;
2. register the toggle command;
3. register player lifecycle listeners;
4. enable already-online players with the use permission;
5. start synchronous selection polling.

Players with the permission are also enabled when they join. A manual toggle choice is session-local and is not persisted across reconnects.

## Command and permission

| Syntax | Permission | Behavior |
|---|---|---|
| `/worldeditvisualizer` | `serverfeatures.feature.worldeditvisualizer.use` | Toggle the visualizer. Player-only. |
| `/wevis` | same | Alias. |
| `/wevis toggle` | same | Toggle the visualizer. |

Other arguments are rejected with the localized usage message. Tab completion suggests `toggle` only when it matches the typed prefix.

The permission is rechecked during polling. Revoking it while the player is online disables the feature and destroys the current client entities on the next poll.

## Configuration

### Materials

| Key | Default | Use |
|---|---|---|
| `edge.material` | `WHITE_STAINED_GLASS` | Stretched wireframe edges. |
| `corner.material` | `LIME_STAINED_GLASS` | Outer cuboid corner markers. |
| `corner.pos1_material` | `BLUE_STAINED_GLASS` | WorldEdit pos1 marker. |
| `corner.pos2_material` | `RED_STAINED_GLASS` | WorldEdit pos2 marker. |

Values are resolved through `Material.matchMaterial`. Missing, unknown, or non-block materials use the documented fallback.

### Glow colors

| Key | Default |
|---|---|
| `glow.edge_color` | `aqua` |
| `glow.corner_color` | `aqua` |
| `glow.pos1_color` | `blue` |
| `glow.pos2_color` | `red` |

Colors use Adventure named colors and are case-insensitive. Invalid values use the corresponding fallback. Displays are full-bright and glowing.

### Geometry and rendering

| Key | Default | Runtime validation |
|---|---:|---|
| `edge.scale` | `0.12` | Wireframe thickness, clamped to `0.02–1.0`. |
| `corner.scale` | `0.35` | Corner and position marker size, clamped to `0.05–2.0`. |
| `render.view_range` | `4.0` | Display view-range multiplier, clamped to `0.1–64.0`. |
| `render.retry_interval_ticks` | `200` | Backoff before retrying the same failed packet render; at least one poll interval. |
| `poll.interval_ticks` | `10` | Selection/permission polling interval, clamped to at least one tick. |

The removed `edge.step_blocks`, `render.max_blocks`, `render.max_distance_blocks`, and `render.resend_interval_ticks` settings belonged to the temporary fake-block implementation and are not used by version 2.0. The constant wireframe does not need a sampling budget or periodic block resend.

### Labels

| Key | Default | Behavior |
|---|---:|---|
| `label.enabled` | `true` | Show pos1/pos2 text displays. |
| `label.y_offset` | `0.7` | Vertical offset above a position marker. |
| `label.scale` | `1.0` | Text-display scale, clamped to `0.1–4.0`. |
| `label.show_prefix_hash` | `false` | Use `#1`/`#2` instead of `pos1`/`pos2`. |

Labels are center-billboarded, see-through, shadowed, full-bright and viewer-only.

## Selection lifecycle

For every enabled online player, polling:

1. obtains the existing WorldEdit session;
2. reads the selection in the player's current world;
3. requires a complete `CuboidRegion`;
4. compares world, minimum, maximum, pos1 and pos2 with the last rendered snapshot;
5. destroys the old packet entities before rendering a changed snapshot;
6. stores the new immutable snapshot and packet handle only after rendering succeeds.

An unchanged selection sends no new packets.

When no complete selection exists or the selector is not cuboid, the previous visualization is destroyed immediately. This fixes the old stale-render behavior.

## Cleanup guarantees

The current client entities are destroyed when:

- the selection changes, is cleared, or becomes unsupported;
- the player toggles the visualizer off;
- the player's use permission is revoked;
- the player changes world;
- the player respawns;
- the feature is disabled or reloaded.

On quit, the server discards its handle without sending cleanup packets because closing the connection already discards every client-only entity.

Rendering is failure-safe. If packet creation or delivery fails partway through a new visualization, every entity ID allocated during that attempt is destroyed before the error is propagated. The previous visualization was already cleared, so a partial replacement cannot remain managed as a valid render.

Repeated failures for the same unchanged selection respect `render.retry_interval_ticks`; a manual toggle bypasses the backoff and retries immediately. This prevents a broken packet conversion from retrying and logging every poll.

Even if a destroy packet cannot be delivered because the player disconnects concurrently, there is still no server entity and therefore no persistent world or server-lag risk.

## Threading and performance

WorldEdit access, Paper entity-metadata creation and packet delivery run on the feature's synchronous lifecycle/polling paths. Bukkit world APIs are never called asynchronously.

Per changed selection, the renderer performs at most 24 unspawned metadata constructions and 48 outbound packets (one spawn plus one metadata packet per entity). Unchanged selections only perform the WorldEdit snapshot comparison.

There is no repeating entity task and no server-side display entity to tick. The only recurring work is bounded selection polling for enabled players.

## Removed legacy API

The generic entity-based visualization API was only used by this feature and has been deleted from `serverfeatures-api`, including:

- `Visualisation` and `VisualHandle`;
- `DisplayVisualHandle`;
- `VisualOptions`;
- `RegionShape` and `CuboidRegionShape`;
- `CubeRegionVisualisation`;
- their obsolete unit tests.

No current source in ServerFeatures references that package.

## Messages

| Key | Use |
|---|---|
| `worldeditvisualizer.enabled` | Toggle result: enabled. |
| `worldeditvisualizer.disabled` | Toggle result: disabled and destroyed. |
| `worldeditvisualizer.no_selection` | No complete selection when enabling manually. |
| `worldeditvisualizer.not_cuboid` | Unsupported selector when enabling manually. |
| `worldeditvisualizer.render_failed` | Packet render failed during a manual enable. |
| `worldeditvisualizer.usage` | Invalid command arguments. |

## Verification checklist

1. Enable with no selection, an incomplete cuboid, a complete cuboid and a non-cuboid selector.
2. Move pos1 and pos2 repeatedly and verify the previous wireframe disappears without world entities accumulating.
3. Clear the selection and change selector type; the old visual must disappear on the next poll.
4. Test one-block, long thin, tall and multi-million-block cuboids; the entity count must remain at or below 24.
5. Have two players visualize different selections and verify neither sees the other's packets.
6. Walk and fly through the wireframe; it must not create collision or alter real block rendering.
7. Change world, respawn, relog, revoke permission, reload the feature and stop the server.
8. Inspect Bukkit/Paper entity counts and chunk data before and after repeated use; they must remain unchanged.
9. Test invalid material/color/scale configuration and verify documented clamping/fallbacks.
10. Verify labels disabled/enabled, coincident pos1/pos2 behavior and invalid command usage.

## Source map

- Defaults, messages and lifecycle: `features/worldeditvisualizer/WorldEditVisualizer.java`
- Selection state and cleanup: `features/worldeditvisualizer/internal/VisualizationService.java`
- Packet renderer: `features/worldeditvisualizer/internal/PacketVisualizationRenderer.java`
- Packet cleanup handle: `features/worldeditvisualizer/internal/PacketVisualHandle.java`
- Constant geometry: `features/worldeditvisualizer/internal/CuboidWireframe.java`
- Join/world/respawn/quit lifecycle: `features/worldeditvisualizer/listener/PlayerLifecycleListener.java`
