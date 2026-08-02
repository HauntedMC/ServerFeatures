# LimitSpawners

> Paper · Feature name `LimitSpawners` · feature package `features.limitspawners` · disabled by default

LimitSpawners enforces a maximum number of surviving mobs for each exact mob-spawner block. A spawner
may create another mob only while the number of living entities attributed to that same world/X/Y/Z
position is below `max_spawn`.

This is a lifetime cap, not a nearby-entity scan. A tracked mob continues to count when it walks away,
teleports, changes chunk, or is unloaded with its chunk. It stops counting when it dies, despawns, is
removed, or is replaced by a successful entity transformation.

## Commands and permissions

The feature has no command, permission, player bypass, PlaceholderAPI expansion, or database table.
Every direct `SpawnerSpawnEvent` is governed by the same configured cap.

## Configuration

File: `plugins/ServerFeatures/features/LimitSpawners/config.yml`.

```yaml
enabled: false
max_spawn: 1
save_interval_ticks: 100
reconcile_interval_ticks: 200
```

| Key | Default | Behaviour |
|---|---:|---|
| `enabled` | `false` | Enables source tagging, durable tracking, reconciliation, and spawn cancellation. |
| `max_spawn` | `1` | Maximum surviving tracked mobs per exact spawner position. Values below zero are clamped to zero. |
| `save_interval_ticks` | `100` | Maximum normal interval between asynchronous atomic registry snapshots. Clamped to at least 20 ticks. Tracked chunk/world unloads request an early snapshot; feature shutdown performs a final synchronous flush. |
| `reconcile_interval_ticks` | `200` | Interval for validating loaded tracked entities and refreshing their last known chunk. Clamped to at least 20 ticks. |

Settings are read when the feature initializes. Reload or re-enable the feature after changing them.

`max_spawn: 0` blocks every direct spawner spawn. Lowering the cap below an existing count does not
remove mobs; the spawner remains blocked until enough tracked mobs are gone.

The old `remove_mobs_on_chunk_unload` setting is no longer used and may be removed from existing
configuration files. Version 2 never deletes mobs merely because a chunk unloads.

## Exact source attribution

Every accepted direct spawner mob receives a persistent entity marker containing:

```text
<source-world-uuid>:<spawner-x>:<spawner-y>:<spawner-z>
```

The marker key is `serverfeatures:limitspawners_source_v2`. It is saved with the entity by Paper and
therefore survives ordinary chunk unloads, server restarts, and feature reloads.

Spawner identity is the exact block position. Two adjacent spawners have independent counts. Replacing
a spawner at the same coordinates intentionally reuses that position's count while surviving mobs from
the previous block still exist.

## What counts

The feature counts:

- direct, uncancelled `SpawnerSpawnEvent` living entities;
- tracked mobs outside the spawner's activation range;
- tracked mobs in another loaded or unloaded chunk;
- tracked mobs that teleport to another world;
- every living replacement produced by a successful `EntityTransformEvent`.

It does not count natural spawns, commands, breeding, plugin-created entities, portal spawns without a
tracked source, or arbitrary mobs standing near a spawner. Descendants created through mechanics that
do not identify their parent through `EntityTransformEvent`, such as some split/duplication mechanics,
are not automatically attributed.

On the first upgrade from the old runtime-only implementation, already-existing mobs have no reliable
source marker and cannot safely be assigned to a spawner. Newly accepted mobs are exact from that point
forward. Once marked entities or the durable registry exist, reloads and restarts reconstruct correctly.

## Spawn event ordering

LimitSpawners checks and reserves a slot at `HIGHEST`, with `ignoreCancelled=true`:

1. read the exact source spawner position;
2. compare its durable count with `max_spawn`;
3. cancel the spawn when the cap is full;
4. otherwise tag and register the entity immediately so another spawn in the same tick sees the
   reservation.

A `MONITOR` handler observes the final cancellation state and rolls back the reservation when another
plugin cancelled after the initial check. This prevents same-tick over-spawning without retaining
cancelled entities in the count.

## Death, removal, movement, unload, and transformation

- `EntityDeathEvent` removes the entity immediately.
- Paper's `EntityRemoveFromWorldEvent` performs a delayed validity check. Actual despawns and plugin
  removals are deleted from the registry.
- Teleports update the record to their explicit destination, including cross-world teleports.
- Ordinary walking does not install a global `EntityMoveEvent` listener. The record's last-known chunk
  is refreshed when the entity is encountered during loaded-entity reconciliation or chunk unload.
- Chunk/world unload marks affected UUIDs as temporarily unloading, updates their last known chunk,
  and keeps them counted. The later remove-from-world event is therefore not mistaken for death.
- Chunk load scans persistent source markers, restores missing runtime entries, and repairs markers
  from durable records. An unresolved record is deliberately retained rather than deleted merely
  because its last-known chunk no longer contains the mob; that location may be stale after an unclean
  shutdown or ordinary cross-chunk movement.
- Successful transformations replace the original entry with every living transformed entity. A
  one-to-many transformation may temporarily put the count above the configured cap; the source
  spawner then remains blocked until the count falls below the limit.

Explicit death and removal events are authoritative for deleting a record. Recovery otherwise fails
closed: an unresolved durable mob continues to count until its real entity is loaded or a confirmed
removal event releases it. This prevents a restart or crash from granting a spawner an early extra slot.

## Durable registry

Unloaded entities cannot be resolved through `Bukkit.getEntity(UUID)`, even though they are still alive
in saved chunk data. LimitSpawners therefore keeps a small durable index at:

```text
plugins/ServerFeatures/cache/LimitSpawners-registry/tracked-mobs.json
```

Each record contains the entity UUID, exact source spawner, and last known entity world/chunk. Snapshots
use a schema version and are written through a same-directory temporary file with fsync and atomic move
where supported. A corrupt or unsupported snapshot is quarantined as
`tracked-mobs.json.corrupt-<timestamp>` instead of preventing the feature from starting. Loaded entity
markers then rebuild the recoverable portion of the index.

The entity marker is authoritative recovery evidence; the registry is required to keep unloaded mobs
counted before their chunks are loaded again.

## Lifecycle

Initialization:

1. load the durable registry;
2. register spawn/removal/teleport/transform/chunk/world listeners;
3. reconcile every currently loaded chunk;
4. start periodic loaded-entity reconciliation and snapshot tasks.

Disable/reload reconciles loaded records and waits up to five seconds for the single in-flight
asynchronous snapshot. A queued or timed-out save is cancelled without interruption; store writes are
serialized so an already-running older snapshot must finish before the newest state is synchronously
flushed. Existing entity markers are intentionally left intact so re-enable can recover them.

## Performance

Normal spawn checks are constant-time map/set operations. Death, removal, teleport, and transformation
updates are also constant-time per tracked entity. The feature deliberately does not subscribe to
Paper's global living-entity movement event, avoiding movement-event allocation and dispatch for every
mob on the server. Periodic reconciliation iterates only tracked UUIDs, not all server entities. Chunk
load scans only that chunk's entities, while chunk unload already exposes the entities being saved.
Snapshot cost is linear in the number of tracked mobs and normally runs once per configured save
interval.

All Bukkit entity and chunk access stays on the server thread. Periodic snapshot I/O runs asynchronously
with at most one write in flight; snapshots are immutable copies captured on the server thread. The
only synchronous persistence is the final shutdown flush after pending write coordination.

## Operational verification

1. Set `max_spawn: 2`, enable the feature, and activate one spawner. Confirm exactly two surviving mobs
   block further spawns.
2. Kill one tracked mob and confirm the same spawner can create exactly one replacement.
3. Place a second spawner beside it and confirm it receives its own independent allowance.
4. Move the first spawner's mobs far away and across several chunk borders; confirm they still block
   that original spawner.
5. Unload and reload the mob chunk while the spawner chunk remains active; confirm the count does not
   reset and no mobs are deleted by the feature.
6. Unload and reload the source-spawner chunk; confirm the cap remains unchanged.
7. Restart the server with tracked mobs in loaded and unloaded chunks; confirm both still count.
8. Reload/disable/re-enable only the feature and confirm loaded marked mobs are reconstructed.
9. Test death, normal distance despawn, `/kill`, and plugin removal; each must release exactly one slot.
10. Teleport a tracked mob across chunks/worlds and confirm it remains attached to its original spawner.
11. Convert a tracked villager/zombie or another transformable mob and confirm the replacement remains
    counted.
12. Set `max_spawn: 0` and confirm all direct spawner spawns are cancelled without registry growth.
13. Have another plugin cancel `SpawnerSpawnEvent` after LimitSpawners' check and confirm no phantom slot
    remains reserved.
14. Inspect the JSON snapshot after activity and verify no `.tmp` files remain after a clean save.
15. Restart immediately after a tracked mob crossed a chunk boundary and confirm its source spawner
    never receives an early extra slot before the mob's destination chunk loads.

## Troubleshooting

- **More mobs exist immediately after upgrading:** old unmarked mobs cannot be attributed safely. Kill
  them once or wait for natural cleanup; all newly spawned mobs are tracked exactly.
- **Spawner remains blocked after mobs leave the area:** intended; the cap follows surviving source
  mobs, not nearby mobs.
- **Spawner remains blocked while a mob chunk is unloaded:** intended; unloaded entities are still
  alive in chunk data.
- **A slot does not release immediately after removal:** removal verification is delayed by two ticks
  to distinguish actual removal from unload ordering.
- **An unresolved record remains after manual world/chunk data editing:** the feature intentionally
  fails closed because it cannot prove that an unloaded entity is dead. Remove the stale registry entry
  during maintenance only after verifying the entity no longer exists in saved world data.
- **Registry was quarantined:** inspect the logged `.corrupt-<timestamp>` path. Loaded PDC-marked mobs
  are rebuilt, while records for currently unloaded entities cannot be recovered until their chunks
  load.
