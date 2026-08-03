# LimitSpawners

> Paper · feature name `LimitSpawners` · version `3.0.0` · disabled by default

LimitSpawners is a performance controller for block-spawner farms. It limits active descendants per
exact source spawner, shares a stronger cap across nearby sources, enforces world/server ceilings,
removes abandoned mobs, restricts local spawner density, and normalizes unsafe spawner settings.

The feature intentionally tracks active mobs only while their farm is loaded. It does not persist mob
locations or force chunks to load.

## Configuration

File: `plugins/ServerFeatures/features/LimitSpawners/config.yml`.

```yaml
enabled: false
farm_radius: 32
block_spawner_minecarts: true

mob_control:
  per_spawner_limit: 4
  per_area_limit: 16
  per_world_limit: 256
  server_limit: 512
  blocked_retry_delay_ticks: 200
  maintenance_interval_ticks: 100
  outside_radius_grace_seconds: 30
  inactive_source_grace_seconds: 30
  maximum_lifetime_seconds: 0
  type_overrides: {}
  # MAGMA_CUBE: 2

placement_control:
  enabled: true
  default_limit: 2
  hard_limit: 6
  bypass_soft_limit_permission: serverfeatures.feature.limitspawners.placement.bypass
  bypass_hard_limit_permission: serverfeatures.feature.limitspawners.placement.hardbypass
  tiers:
    tier_1:
      permission: serverfeatures.feature.limitspawners.placement.tier1
      limit: 3
    tier_2:
      permission: serverfeatures.feature.limitspawners.placement.tier2
      limit: 4
    tier_3:
      permission: serverfeatures.feature.limitspawners.placement.tier3
      limit: 5
    tier_4:
      permission: serverfeatures.feature.limitspawners.placement.tier4
      limit: 6

spawner_safety:
  enabled: true
  max_spawn_count: 4
  minimum_spawn_delay_ticks: 200
  max_required_player_range: 16
  max_spawn_range: 4
  max_nearby_entities: 6

position_index:
  save_debounce_ticks: 20
```

There is no compatibility parser for earlier LimitSpawners schemas. Invalid or missing values receive
the generated defaults. Values below zero are clamped where appropriate. `farm_radius` is raised when
needed so it remains at least twice `max_required_player_range`. `server_limit` cannot be lower than
`per_world_limit`. Maintenance and position-save intervals are clamped to at least 20 ticks so an unsafe
configuration cannot schedule either operation every server tick.

`maximum_lifetime_seconds: 0` disables fixed lifetime cleanup. This is the recommended default because
players should normally kill mobs while actively using a farm.

## Active-mob limits

Every accepted block-spawner entity receives a PDC marker containing its exact source world UUID and
block coordinates. Runtime indexes provide:

- exact source count;
- active mob count for sources inside `farm_radius`;
- world count;
- server count;
- mob-chunk and source-chunk cleanup lookups.

A spawn is accepted only when all four configured limits have capacity. The entity UUID is reserved
immediately at `HIGHEST`, then finalized one tick after the complete event dispatch. Later cancellation
or failed entity creation rolls the reservation back.

During the one-tick startup bootstrap, direct block-spawner output and player spawner placement remain
blocked. Loaded chunks are reconciled and any crash-surviving marked mobs are removed before normal
operation is enabled. A failed bootstrap leaves the feature fail-closed and logs the failure.

A blocked source receives at least `blocked_retry_delay_ticks` before its next cycle. No player message
or log is emitted for routine blocked attempts.

Spawner minecarts have no exact block source. When `block_spawner_minecarts` is enabled their output is
cancelled.

## Cleanup behavior

A tracked mob is released when it dies, despawns, is removed, or is replaced by an accepted
transformation.

The mob is actively removed without drops or XP when:

- its own chunk unloads;
- its source-spawner chunk unloads;
- its source block disappears;
- its source is disabled through SpawnerToggle;
- it teleports to another world;
- it remains outside `farm_radius` for the configured grace period;
- its source remains inactive for the configured grace period;
- the optional maximum lifetime expires;
- the feature disables or reloads.

The maintenance pass iterates only active source spawners and their tracked mobs. It does not scan world
entities or install an entity-movement listener.

## Transformations and descendants

Successful `EntityTransformEvent` replacements inherit the source. A transformation is cancelled when
its complete result would exceed a source, area, world, or server cap.

Tracked slime and magma-cube splits reserve only the number of children that fit. Child capacity
discounts the parent while it is still waiting for removal, preventing both false rejection and cap
overflow. The subsequent `SLIME_SPLIT` spawn events inherit the parent source. Tracked shulker
duplication also inherits the source and is cancelled when no capacity remains.

Ordinary breeding descendants are not attributed; that belongs to a general breeding/entity limiter.

## Placement density

Every player placement of `Material.SPAWNER` is checked independently of SilkSpawners. The prospective
position is evaluated with every indexed spawner inside a three-dimensional sphere of `farm_radius`.
Natural, disabled, different-type, and other-player spawners all count.

The effective limit is the highest configured permission tier the player has, clamped to `hard_limit`.
The soft bypass grants exactly the hard limit. Only the separate hard-bypass permission permits an
unlimited administrative placement.

Placement positions remain provisional for the complete event and are committed one tick after final
cancellation and block-state validation. Reconciliation explicitly excludes every same-tick provisional
position, while the pending-reservation count includes it exactly once. Same-tick placement attempts
therefore cannot race past a limit or be double-counted.

## Persistent position index

Only spawner block positions are persisted:

```text
plugins/ServerFeatures/cache/LimitSpawners-positions/spawner-index.json
```

The versioned JSON file uses a same-directory temporary file, fsync, atomic replacement when supported,
and corruption quarantine. Writes are asynchronous, debounced, and limited to one in flight; shutdown
performs a coordinated final flush.

Chunk loads reconcile actual `CreatureSpawner` tile entities with the index. Placement checks also
reconcile already-loaded chunks intersecting their radius. No query force-loads an unloaded chunk.

## Crash recovery

Mob state is not persisted. The PDC source marker exists only so a mob surviving an abrupt crash can be
recognized. Marked survivors are removed during the delayed startup scan when already loaded, or when
their entities later load. Clean shutdown removes every tracked mob before clearing runtime state.

## Spawner safety

Safety clamps run after placement, during chunk reconciliation, and before a source spawn:

- `spawnCount` is capped;
- minimum delay is raised;
- maximum delay is raised before the minimum when required by API invariants;
- required player range is capped and non-positive values are repaired;
- spawn range is capped;
- maximum nearby entities is capped.

Only unsafe values are changed. Entity type and potential-spawn data are not rewritten.

## Commands and permissions

| Command | Permission | Behavior |
|---|---|---|
| `/limitspawners stats` | `serverfeatures.feature.limitspawners.command.stats` | Active totals and reason counters. |
| `/limitspawners inspect` | `serverfeatures.feature.limitspawners.command.inspect` | Inspects the targeted spawner within eight blocks. |
| `/limitspawners cleanup spawner` | `serverfeatures.feature.limitspawners.command.cleanup` | Removes tracked mobs from the targeted source. |
| `/limitspawners cleanup radius <blocks>` | same | Removes tracked mobs whose sources are inside the radius. |
| `/limitspawners cleanup world [world]` | same | Removes tracked mobs in a world. |
| `/limitspawners rescan loaded` | `serverfeatures.feature.limitspawners.command.rescan` | Reconciles currently loaded chunks only. |

Cleanup commands never remove spawner blocks.

## Integration

SilkSpawners continues to own typed item creation, mining permission, placement permission, and spawned
entity-type restoration. LimitSpawners independently owns density, active mobs, cleanup, and safety.

SpawnerToggle uses a persistent disabled marker. LimitSpawners reads that marker before every spawn and
during maintenance, and cleans the source when it becomes disabled. There is no hard feature API or
LuckPerms dependency.

Protection plugins are respected through cancelled block events. Permanent placement/index updates are
always deferred until the final event result is known.

## Performance invariants

- no `EntityMoveEvent`;
- no forced chunk loads;
- no complete world entity scan;
- no persistent per-mob registry;
- no disk writes caused by mob movement;
- no asynchronous Bukkit API calls;
- no concurrent collections for main-thread runtime state;
- no direct LuckPerms dependency;
- maintenance cost bounded by the configured server active-mob limit.

## Verification checklist

1. Confirm the fifth mob is rejected with a four-mob source cap and killing one permits one replacement.
2. Confirm nearby sources share the area cap while distant sources do not.
3. Confirm world and server ceilings reject additional output.
4. Unload a mob chunk and verify the tracked mob does not return.
5. Unload a source chunk while its mobs remain elsewhere and verify they are removed.
6. Break and explode a source and verify cleanup occurs only after the final event result.
7. Move a tracked mob outside the radius, return during grace, then remain outside past grace.
8. Leave an otherwise force-loaded farm inactive past the configured grace.
9. Test transformations, slime/magma splits, and shulker duplication at capacity boundaries.
10. Test every placement tier, soft bypass, hard bypass, vertical radius, and same-tick placement.
11. Place unsafe custom spawner data and verify every configured clamp.
12. Test SilkSpawners and SpawnerToggle together and independently.
13. Restart cleanly and after simulated abrupt termination; no marked survivor should remain active.
14. Corrupt the position file and verify quarantine plus loaded-chunk reconstruction.
15. Run `/limitspawners stats`, `inspect`, cleanup scopes, and `rescan loaded`.
