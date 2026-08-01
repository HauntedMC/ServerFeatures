# LimitSpawners

> Paper · Feature name `LimitSpawners` · feature package `features.limitspawners` · disabled by default

LimitSpawners enforces a runtime cap on the number of currently tracked living entities produced by each individual mob-spawner block. It does **not** limit spawner block placement, spawner density, chunks, claims, entity types or players. Every unique spawner block position has its own bucket keyed by world UUID and exact X/Y/Z.

The cap is in-memory and best-effort. Tracking begins only for spawns observed while the feature is active and is dropped on relevant chunk/world unload; it is not rebuilt by scanning existing entities after restart/reload.

## Commands and permissions

No command and no permission are registered. Every uncancelled `SpawnerSpawnEvent` is subject to the same cap.

There is no bypass permission, per-world/entity/spawner override, administration query or PlaceholderAPI output.

## Complete configuration reference

File: `plugins/ServerFeatures/features/LimitSpawners/config.yml`.

| Key | Default | Exact behaviour and edge cases |
|---|---:|---|
| `enabled` | `false` | Constructs runtime indexes and registers spawn/death/remove/transform/chunk/world listeners. |
| `max_spawn` | `1` | Maximum tracked living entities per exact spawner block. Handler fallback is `4` if node cannot be read, differing from generated default. Value is clamped to at least zero. |
| `remove_mobs_on_chunk_unload` | `true` | When true, hard-removes tracked living entities in an unloading chunk and sets `removeWhenFarAway=true` on registered/transformed entities. When false, entities are not hard-removed, but spawner buckets in the unloading chunk are still discarded. |

Settings are cached at handler construction. Reload/re-enable for changes.

### `max_spawn: 0`

Every observed spawner spawn is rejected because bucket size `>= 0`. No entity is registered.

### What the cap counts

Only entities whose `SpawnerSpawnEvent` was allowed and successfully registered during the current feature runtime, less entries later unregistered/pruned/transferred. It does not count:

- entities spawned before feature enable;
- entities loaded from disk after bucket state was dropped;
- mobs spawned by commands, breeding, natural spawning, plugins, portals or transformations not originating from a tracked entity;
- untracked entities near a spawner;
- one spawner's mobs in another spawner's bucket.

## Spawner identity

`SpawnerKey` contains:

- world UUID;
- block X;
- block Y;
- block Z.

String form:

```text
<world-uuid>:<x>:<y>:<z>
```

The helper can parse this form, but the feature does not persist or expose it in commands/config.

Replacing a spawner at the same coordinates during one runtime reuses the same key/bucket until stale entities are pruned or bucket removed.

## Runtime indexes

Two concurrent maps:

```text
buckets: SpawnerKey -> concurrent set of entity UUIDs
reverse: entity UUID -> SpawnerKey
```

The reverse map supports constant-time unregister/transform/chunk cleanup.

### Lazy pruning

Before registration/count queries, a bucket set removes UUIDs for which:

- `Bukkit.getEntity(uuid)` is not a `LivingEntity`;
- entity is dead;
- entity is invalid.

Pruning removes only the bucket-set UUID, not its stale reverse-map entry. If an entity disappears without the explicit removal events completing, the reverse map can retain stale entries until chunk/world cleanup or feature disposal.

The comment says pruning is bounded by `maxSpawn`, but transform races/invalid config/history can temporarily make a set larger.

## Spawn event contract

```java
@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onSpawnerSpawn(SpawnerSpawnEvent event)
```

Flow:

1. obtain `event.getSpawner()` block state;
2. cast `event.getEntity()` directly to `LivingEntity`;
3. create exact-position key from spawner location;
4. prune bucket;
5. if size >= cap, cancel event;
6. otherwise add entity UUID to bucket/reverse;
7. when remove-on-unload is true, set `entity.setRemoveWhenFarAway(true)`.

### Ordering and failure boundaries

- Events cancelled before `HIGHEST` are ignored/not counted.
- Registration happens before final event outcome. A later same/higher-nonstandard listener cancelling the spawn can leave a tracked entity that never fully spawns until death/remove/pruning cleans it.
- Direct cast assumes every spawner-spawn entity is living. Vanilla spawners normally satisfy this; a plugin producing a non-living entity through this event would throw `ClassCastException`.
- `setRemoveWhenFarAway(true)` changes normal despawn policy even if the entity type/plugin would prefer otherwise.
- Cancellation does not remove the entity directly; Paper handles cancelled spawn.

The limit is checked with concurrent set size followed by add, not an atomic reservation across simultaneous calls. Bukkit spawn events are normally main-thread serialized, so this is acceptable under normal operation; off-thread/plugin event invocation could exceed cap.

## Death and removal cleanup

### `EntityDeathEvent`

```java
priority = MONITOR, ignoreCancelled = true
```

Removes reverse mapping, removes UUID from bucket, and deletes empty bucket.

### Paper `EntityRemoveFromWorldEvent`

```java
priority = MONITOR, ignoreCancelled = true
```

Schedules unregister **five ticks later**.

The delay accommodates transformations/removal ordering, but creates a temporary count window. If an entity is removed and a replacement/other spawn occurs before cleanup, the old UUID can still count unless transform transfer updates it.

The delayed callback captures the original entity object and asks for its UUID; no world/entity API beyond UUID is required by unregister.

There is no direct listener for every possible plugin removal API beyond Paper's event.

## Entity transformation

```java
@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onTransform(EntityTransformEvent event)
```

When transformed entity list is non-empty and its **first** entity is living:

1. find original UUID in reverse map;
2. add replacement UUID to same bucket/reverse without cap check;
3. set remove-when-far-away when configured;
4. remove old reverse/bucket UUID;
5. delete bucket only when empty.

This is intended as a one-for-one transfer.

Limitations:

- only first transformed entity is tracked; multi-result transformations ignore additional living replacements;
- transfer occurs at `HIGHEST` before final event outcome; later cancellation can leave replacement tracking inconsistent;
- new UUID is added before old removed, so bucket briefly contains both;
- a replacement UUID already tracked elsewhere is overwritten in reverse without removing it from old bucket;
- no cap check is appropriate for true one-to-one replacement but can exceed cap for unusual transforms.

## Chunk unload semantics

At `ChunkUnloadEvent` `MONITOR`, `ignoreCancelled=true`:

### When `remove_mobs_on_chunk_unload=true`

Iterate `chunk.getEntities()` and for every living entity whose UUID exists in reverse:

1. remove reverse mapping;
2. call `entity.remove()`.

The entity UUID is **not explicitly removed from its bucket set** in this loop. The subsequent bucket-key removal only removes buckets whose **spawner position** lies in the unloading chunk.

Therefore, if a mob spawned by a spawner in chunk A has moved into unloading chunk B:

- mob is removed and reverse mapping deleted;
- bucket for spawner A remains because A is not unloading;
- stale UUID remains in A's bucket until next registration/count prune.

### Always

Remove every bucket whose spawner coordinates lie inside the unloading chunk. This happens regardless of `remove_mobs_on_chunk_unload`.

When false, tracked mobs can remain/load elsewhere while their originating spawner bucket is forgotten. On chunk reload, that spawner starts with an empty runtime count and can spawn another full cap, so total surviving descendants can exceed cap.

When true, only tracked entities physically in the unloading chunk are hard-removed; tracked entities from that spawner that moved to another loaded chunk survive while the bucket is dropped when the spawner chunk unloads, also allowing future over-cap after reload.

The feature is therefore a loaded-runtime throttle, not a durable lifetime cap.

## World unload

`WorldUnloadEvent` at `MONITOR` calls `dropWorld(worldUUID)`:

- removes all bucket keys in world;
- removes all reverse entries whose key world matches.

It does not explicitly remove tracked entities. World unload itself handles world entities. State is not rebuilt on world load.

## `currentAliveCount`

Public handler method returns bucket size after lazy prune. No command/API registration exposes it, but other Java code holding the feature handler can call it.

`resolveWorld(UUID)` is also exposed but unused by current feature.

## Player/spawner placement scope

Despite the feature name and old documentation, there is no listener for:

- `BlockPlaceEvent`;
- `BlockBreakEvent`;
- spawner item placement;
- SilkSpawners;
- claims/WorldGuard;
- spawner ownership;
- chunk density.

Spawner blocks are unrestricted. Only their observed spawn output is capped.

## Performance

Normal spawn cost is bounded by bucket size plus `Bukkit.getEntity` lookups during prune. At small caps this is cheap.

Chunk unload iterates every entity in the chunk and performs reverse-map lookup. World drop scans map keys/entries.

Maps can accumulate stale reverse entries where pruning removed bucket UUIDs without reverse cleanup. Long-running servers with unusual remove paths should monitor memory; explicit consistent pruning would be safer.

## Persistence, database and messaging

None. No PDC on entities, DataProvider, database, file cache, Redis, proxy message, API registration or PlaceholderAPI expansion.

`removeWhenFarAway` is ordinary entity runtime state and may be persisted by Paper depending on save timing, but no feature ownership marker is written.

## Lifecycle and disable

Initialization constructs handler and registers transform listener followed by main listener.

`disable()` is empty. Lifecycle unregisters listeners/delayed tasks, but the feature does not:

- clear maps explicitly;
- restore `removeWhenFarAway` values;
- remove tracked mobs;
- persist counts.

Old handler/maps become unreachable. Existing mobs remain, and re-enable starts counting only new observed spawns.

## Developer source map

- Defaults/lifecycle: `features/limitspawners/LimitSpawners.java`
- Runtime buckets/reverse/cleanup: `features/limitspawners/internal/LimitSpawnersHandler.java`
- Spawn/death/remove/chunk/world events: `features/limitspawners/listener/LimitSpawnersListener.java`
- Transform transfer: `features/limitspawners/listener/TransformListener.java`
- Exact key: `features/limitspawners/model/SpawnerKey.java`
- Key tests: `src/test/.../features/limitspawners/model/SpawnerKeyTest.java`

## Operational verification

1. Verify cap independently for two adjacent spawner blocks and multiple entity types.
2. Enable feature with existing spawned mobs; confirm they are not counted.
3. Test max 0, 1 and larger values.
4. Cancel spawn before/after `HIGHEST` and inspect stale tracking.
5. Kill, despawn, plugin-remove and invalidate tracked entities; verify delayed/lazy cleanup.
6. Test zombie-villager and other transforms, including multi-result plugin transforms.
7. Move mobs away from source chunk, unload mob/spawner chunks separately with both config modes.
8. Reload/restart and confirm no tracking reconstruction.
9. Inspect `removeWhenFarAway` side effects.
10. Stress unusual removal paths and monitor reverse-map growth.
11. Verify spawner placement is intentionally unrestricted.

## Troubleshooting

- **Spawner blocks are not limited:** feature limits spawned living entities per exact spawner, not block placement/density.
- **More mobs than cap exist:** pre-existing/untracked mobs, chunk state drops, mobs moved between chunks, restart/reload or transformed edge cases are not durable-counted.
- **Mobs disappear on chunk unload:** intentional when `remove_mobs_on_chunk_unload=true` and entity is tracked in that chunk.
- **Spawner resumes full count after chunk reload:** bucket is always dropped when source chunk unloads.
- **Cap remains full after mob removed:** cleanup is delayed five ticks or lazy prune occurs on next spawn/count.
- **Memory grows:** stale reverse entries can survive lazy bucket pruning; inspect unusual remove paths.
- **Config default appears as 4 unexpectedly:** runtime fallback is 4 while generated `max_spawn` is 1.
