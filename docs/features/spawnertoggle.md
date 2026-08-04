# SpawnerToggle

> Paper · feature ID `spawnertoggle` · version `2.0.0` · disabled by default · placed block spawners only

SpawnerToggle lets players enable or disable a placed block spawner without changing its normal
activation range. Disabled state is stored as a persistent block-state PDC marker and every
`SpawnerSpawnEvent` from that source is cancelled while the marker is present.

This replaces the unsafe range-zero convention. Paper treats non-positive required-player range as
always active, so range zero must not be used as an off switch.

## Configuration

```yaml
enabled: false
toggle_permission: ""
```

An empty permission allows every player who passes protection checks. A non-empty value requires that
permission. There is no compatibility parser for the removed `default_spawn_range` setting.

## Interaction flow

Only main-hand `RIGHT_CLICK_BLOCK` interactions targeting `Material.SPAWNER` are considered. The action
is finalized one tick after event dispatch, so any protection plugin cancelling the interaction at a
later priority is respected.

Before toggling:

1. the block must still be a spawner;
2. the optional configured permission must pass;
3. when GriefPrevention is currently enabled, its break authorization must pass.

GriefPrevention availability is resolved for every interaction rather than cached at feature startup,
so enabling or disabling that plugin does not leave stale integration state.

The PDC marker is then added or removed and the block state is force-updated without physics. Spawned
type, delay, ranges, counts, and potential-spawn data remain unchanged.

## Spawn enforcement

A `SpawnerSpawnEvent` listener at `HIGHEST` cancels output from marked sources. This makes SpawnerToggle
correct even when LimitSpawners is disabled.

When LimitSpawners is enabled, it reads the same marker before accepting output and removes already
tracked mobs after a source is switched off.

## Messages

| Key | Purpose |
|---|---|
| `spawner_toggle.toggle_message` | Outer toggle result; `{status}` is a rendered component. |
| `spawner_toggle.status_on` | Enabled status. |
| `spawner_toggle.status_off` | Disabled status. |
| `spawner_toggle.claim_restricted` | GriefPrevention denied access. |
| `general.no_permission` | Configured toggle permission denied. |

## Persistence

The marker is stored in the placed spawner's `PersistentDataContainer`, so it survives chunk unload,
restart, world save, and compatible block-copy operations. Mining and re-placing a spawner may not
preserve the marker because the item format belongs to SilkSpawners.

Disable/reload of the feature does not alter existing markers. A disabled source remains disabled when
the feature is enabled again.

## Performance

The feature performs no scans or repeating tasks. Normal cost is one delayed block-state validation per
qualifying interaction and one marker lookup per block-spawner spawn attempt.

## Verification checklist

1. Toggle a source off and confirm every direct block-spawner spawn is cancelled.
2. Toggle it on and confirm normal spawning resumes without range changes.
3. Verify a cancelled interaction never changes the marker.
4. Verify main-hand filtering prevents duplicate off-hand toggles.
5. Test empty and configured `toggle_permission` values.
6. Test GriefPrevention owner, trusted, untrusted, wilderness, enable, and disable states.
7. Restart and unload/reload the chunk while the source is disabled.
8. Enable LimitSpawners and confirm toggle-off also cleans active tracked mobs.

## Source map

- lifecycle, configuration, messages, and state mutation: `features/spawnertoggle/SpawnerToggle.java`
- PDC contract: `features/spawnertoggle/SpawnerToggleState.java`
- interaction ordering and spawn cancellation: `features/spawnertoggle/listener/SpawnerInteractListener.java`
