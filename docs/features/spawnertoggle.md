# SpawnerToggle

> Paper · feature ID `spawnertoggle` · version `2.1.0` · disabled by default · placed block spawners only

SpawnerToggle lets players enable or disable a placed block spawner while preserving the spawner's real
server-side settings. Disabled state is stored as a persistent block-state PDC marker and every
`SpawnerSpawnEvent` from that source is cancelled while the marker is present.

Disabled spawners also reproduce the previous stopped-spin visual cue. This is now sent as a client-only
block-entity update with a visual required-player range of zero. The real placed spawner keeps its normal
activation range, because Paper treats a non-positive server-side required-player range as always active.

## Configuration

```yaml
enabled: false
toggle_permission: ""
```

An empty permission allows every player who passes protection checks. A non-empty value requires that
permission. There is no compatibility parser for the removed `default_spawn_range` setting because the
real activation range is never changed.

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

The PDC marker is then added or removed and the real block state is force-updated without physics.
Spawned type, delay, ranges, counts, and potential-spawn data remain unchanged. Every player who already
has the chunk receives the corresponding visual block-entity state immediately.

## Visual synchronization

When disabled, viewers receive a copied spawner tile state whose required-player range is zero. This
recreates the non-spinning in-cage mob visualization without writing range zero to the world.

`PlayerChunkLoadEvent` reapplies this client-only state whenever a player receives a chunk containing a
disabled spawner. Feature enable also refreshes already loaded chunks for online players. Toggling the
spawner on, or disabling/reloading the feature, sends the actual tile state back so stale client visuals
are cleared.

The visual update is sent only when Paper reports that the viewer has already received the chunk.

## Spawn enforcement

A `SpawnerSpawnEvent` listener at `HIGHEST` cancels output from marked sources. This makes SpawnerToggle
correct even when LimitSpawners is disabled.

When LimitSpawners is enabled, it reads the same marker before accepting output and removes already
tracked mobs after a source is switched off. The visual-only range change never reaches LimitSpawners or
the real block state.

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
the feature is enabled again, while its client visual is restored when the feature resumes.

## Performance

There is no repeating task. Normal cost is one delayed block-state validation per qualifying
interaction, one marker lookup per block-spawner spawn attempt, and a scan of the tile entities in a
chunk when that chunk is sent to a player. Only disabled spawners produce client block-entity updates.

Loaded chunks are scanned once during feature enable and disable so online viewers cannot retain stale
visual state across a feature reload.

## Verification checklist

1. Toggle a source off and confirm every direct block-spawner spawn is cancelled.
2. Confirm the mob inside the disabled spawner stops spinning for all current viewers.
3. Walk out of view distance and return; confirm the stopped visual is reapplied after chunk delivery.
4. Toggle the source on and confirm the mob resumes spinning and normal spawning resumes.
5. Verify the real required-player range remains unchanged across both toggles.
6. Verify a cancelled interaction never changes the marker or visual.
7. Verify main-hand filtering prevents duplicate off-hand toggles.
8. Test empty and configured `toggle_permission` values.
9. Test GriefPrevention owner, trusted, untrusted, wilderness, enable, and disable states.
10. Restart and unload/reload the chunk while the source is disabled.
11. Enable LimitSpawners and confirm toggle-off also cleans active tracked mobs.
12. Disable/reload SpawnerToggle and confirm online clients receive the actual spawner state again.

## Source map

- lifecycle, configuration, messages, and state mutation: `features/spawnertoggle/SpawnerToggle.java`
- PDC contract: `features/spawnertoggle/SpawnerToggleState.java`
- client-only tile-state rendering: `features/spawnertoggle/SpawnerVisualService.java`
- interaction, chunk-view synchronization, and spawn cancellation: `features/spawnertoggle/listener/SpawnerInteractListener.java`
