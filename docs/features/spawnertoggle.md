# SpawnerToggle

> Paper · Feature ID `spawnertoggle` · disabled by default · right-click interaction only

SpawnerToggle lets a player right-click a placed mob spawner and switch its Bukkit `requiredPlayerRange` between the configured normal range and `0`. A range equal to the configured value is interpreted as “on” and changed to zero; every other current range is interpreted as “off/custom” and reset to the configured value.

The state is not stored in a separate database or PDC. It is the ordinary `CreatureSpawner` block-state field itself and therefore persists with the world/chunk tile entity.

## Commands, permissions and placeholders

SpawnerToggle registers:

- no command;
- no permission node;
- no PlaceholderAPI expansion;
- no public service API;
- no database, Redis or plugin-messaging integration.

Every player who can reach the interaction listener can attempt to toggle a spawner. Access control is supplied only by the optional GriefPrevention check described below; when GriefPrevention is absent, there is no feature-owned authorization check.

## Configuration

| Key | Default | Meaning |
|---|---:|---|
| `enabled` | `false` | Registers the interaction listener. |
| `default_spawn_range` | `16` | The `CreatureSpawner.requiredPlayerRange` value considered enabled and restored when toggling on. |

The range is read on every toggle with a direct cast to `int`; wrong config types can throw during interaction. There is no range clamp.

Important values:

- a positive value such as `16` gives the intended two-state behavior `16 ↔ 0`;
- `0` makes the “off” and configured “on” values identical, so a spawner at zero repeatedly reports/takes the off branch without changing state;
- negative or unusually large values are passed to the Bukkit block state and depend on API validation/server behavior.

There are no settings for interaction item, entity type, world, cooldown, particle/sound, permission or alternate protection integrations.

## Interaction event and ordering

`PlayerInteractEvent` is handled at `HIGHEST` with no `ignoreCancelled` declaration.

A toggle is attempted only when:

1. the event hand is `EquipmentSlot.HAND` (main hand);
2. action is `RIGHT_CLICK_BLOCK`;
3. clicked block exists and is `Material.SPAWNER`;
4. optional GriefPrevention authorization passes.

The held item is irrelevant. Empty hand, blocks, tools and the typed spawner item produced by SilkSpawners all behave the same when right-clicking a placed spawner.

The listener does **not** cancel the interaction before or after toggling. Consequences:

- another plugin's right-click behavior may also run;
- the player's held item's normal use can continue;
- a listener that already cancelled the event at a lower priority does not stop SpawnerToggle, because cancelled events are still received;
- a later listener can cancel the interaction after the spawner has already been modified, but cancellation does not automatically roll back block-state changes.

This differs from a normal “respect all protection cancellation” model. GriefPrevention has a dedicated direct check, but other claim/protection plugins that only cancel `PlayerInteractEvent` may not prevent the toggle.

## Toggle algorithm

`toggleSpawner` obtains the current block state and returns unless it is `CreatureSpawner`.

```text
if current requiredPlayerRange == configured default:
    set requiredPlayerRange to 0
    report status_off
else:
    set requiredPlayerRange to configured default
    report status_on
update block state
```

`BlockState#update()` is called with default arguments. The feature does not force-update an unloaded/replaced block and does not request physics.

### Meaning of “off”

The implementation does not have a native enabled flag. It uses required-player range zero as an effective off state. Whether a particular server version treats zero as completely inactive is determined by vanilla/Paper spawner activation logic. The feature does not cancel `SpawnerSpawnEvent` or monitor actual spawn attempts.

### Existing custom ranges

Any value different from `default_spawn_range` is treated as off and replaced with the configured default. Examples with default 16:

- current `16` → `0`, reported disabled;
- current `0` → `16`, reported enabled;
- current `8` → `16`, reported enabled;
- current `32` → `16`, reported enabled.

Thus the feature cannot preserve custom per-spawner activation ranges and has no marker distinguishing its own zero from a zero set by another plugin.

## GriefPrevention integration

At feature initialization, `Bukkit.getPluginManager().isPluginEnabled("GriefPrevention")` is captured in a boolean. It is not re-evaluated per interaction.

When true, the feature calls:

```text
GriefPrevention.instance.dataStore.getClaimAt(location, false, null)
```

Behavior:

- no claim → allow;
- claim exists → call deprecated `claim.allowBreak(player, Material.SPAWNER)`;
- null denial reason → allow;
- non-null denial reason → reject and send `spawner_toggle.claim_restricted`.

The actual GriefPrevention denial component/reason is not shown; the feature sends its own fixed localization message.

The check uses break permission as a proxy for “may edit/toggle this spawner.” Container/access/trust-specific permissions are not consulted separately.

Operational boundaries:

- GriefPrevention must be available as a compatible runtime dependency for direct class references;
- enabling/disabling/reloading GriefPrevention after SpawnerToggle initialization does not update the captured flag;
- when the captured flag is false, no direct protection check runs;
- when true but GriefPrevention later becomes unavailable/internally uninitialized, direct static access can fail during interaction.

## Messages

| Key | Variables / behavior |
|---|---|
| `spawner_toggle.toggle_message` | `{status}` receives a rendered Adventure component, not a plain string. |
| `spawner_toggle.status_on` | Rendered for the player when range is set to the configured default. |
| `spawner_toggle.status_off` | Rendered when range is set to zero. |
| `spawner_toggle.claim_restricted` | GriefPrevention direct check denied access. |

The default toggle message mixes English and Dutch. Status components are built for the same audience before being inserted into the outer message.

## Persistence and lifecycle

Toggle state persists as part of the world block entity's normal `requiredPlayerRange`. Therefore it generally survives:

- chunk unload/load;
- server restart;
- feature disable;
- world save/copy tools that preserve full spawner block state.

The feature itself persists nothing and performs no startup scan, migration or cleanup. Disable does not restore ranges.

When a spawner is mined into an item, preservation depends on the mining plugin/item serialization. SilkSpawners' item creator deliberately preserves entity type but does not explicitly copy a placed spawner's `requiredPlayerRange`; a disabled range can therefore be lost when picked up and replaced.

WorldEdit/clone operations may copy the block-state range as ordinary NBT, but this feature does not validate or reconcile copied values.

## Interactions with SilkSpawners

`ItemUtils` in SilkSpawners gives generated items lore that says:

```text
Right-click: Toggle Mob Spawning
```

SpawnerToggle supplies that behavior only for a **placed** spawner. It does not process right-clicking the item in air or inventory.

There is no formal API or PDC contract between the features. Any placed `Material.SPAWNER`, including vanilla/untyped/plugin-created blocks, is eligible for range toggling.

## Threading and performance

All work occurs synchronously inside the main-thread interact event:

- optional GriefPrevention claim lookup;
- block-state read;
- localization builds/messages;
- block-state update.

There are no tasks, caches, sweeps or asynchronous calls. Cost is one claim lookup and tile-state update per qualifying right click.

## Important implementation boundaries

- No permission is required.
- Cancelled interaction events are still processed.
- The event is not cancelled after handling.
- Only main-hand events are accepted, preventing the usual duplicate off-hand event.
- Any held item is accepted.
- Only `requiredPlayerRange` changes; spawned type and all other spawner settings remain untouched.
- Zero is a convention for off, not a dedicated state flag.
- Any non-default range is overwritten as if off.
- GriefPrevention support is detected once at initialization.
- Other protection plugins are not directly integrated.
- There is no rollback if another listener later cancels the event.
- Disable does not re-enable previously disabled spawners.
- Config type/range is not validated.

## Verification checklist

1. Right-click a spawner in main hand with empty hand and several item types.
2. Verify `default_spawn_range ↔ 0` and inspect the actual block-state field after each click.
3. Start with custom ranges below/above the default and confirm they are normalized to default.
4. Test `default_spawn_range=0`, negative and large values in a disposable environment.
5. Verify off-hand events do not double-toggle.
6. Cancel `PlayerInteractEvent` from another plugin at LOW/NORMAL and confirm SpawnerToggle still runs.
7. Add a later HIGHEST/MONITOR cancellation and confirm no automatic rollback.
8. Test GriefPrevention owner, trusted user, untrusted user and wilderness behavior.
9. Reload/disable GriefPrevention after feature initialization and observe the captured integration state.
10. Mine/re-place an off spawner with SilkSpawners and inspect whether range state survives.
11. Copy the block using production WorldEdit/backup tooling and inspect the copied range.
12. Disable/reload SpawnerToggle and confirm existing ranges remain unchanged.

## Source map

- Defaults, toggle algorithm and GriefPrevention check: `features/spawnertoggle/SpawnerToggle.java`
- Interaction filters and event priority: `features/spawnertoggle/listener/SpawnerInteractListener.java`
- Metadata: `features/spawnertoggle/meta/Meta.java`
