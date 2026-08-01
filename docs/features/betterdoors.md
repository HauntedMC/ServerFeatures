# BetterDoors

> Paper · Feature name `BetterDoors` · feature package `features.betterdoors` · disabled by default

BetterDoors synchronizes valid vanilla double doors and adds a knock sound when a player left-clicks any door. It handles player interaction and redstone changes only. Trapdoors, fence gates, permissions, regions, claims and custom animation systems are not part of the implementation.

## Behaviour at a glance

- Right-clicking one half of a valid double door mirrors the paired door on the next tick, after vanilla has toggled the primary.
- A redstone power transition mirrors the paired door immediately to the implied powered/open state.
- Main-hand left-click on any Bukkit `Door` plays a wooden- or non-wood knock sound.
- Pairing requires the same material, same facing, opposite hinge, and the expected perpendicular adjacent block.
- The feature does not cancel interaction or redstone events and does not check build/protection permissions itself.
- There are no commands, permissions, persistent state, tasks beyond one-tick mirrors, APIs, database records, Redis messages, or PlaceholderAPI expansions.

## Complete configuration reference

File: `plugins/ServerFeatures/features/BetterDoors/config.yml`.

| Key | Default | Meaning and edge cases |
|---|---:|---|
| `enabled` | `false` | Enables the three event handlers and handler construction. |
| `knock_wood_volume` | `1.0` | Volume for `ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR`. Read once during initialization as `Double`, converted to float, and clamped to `0..Float.MAX_VALUE`. |
| `knock_wood_pitch` | `1.0` | Pitch for the wooden-door knock sound. Same conversion/clamp. |
| `knock_other_volume` | `1.0` | Volume for `ENTITY_ZOMBIE_ATTACK_IRON_DOOR`, used for every door not in Bukkit's `Tag.WOODEN_DOORS`, including iron and copper variants. |
| `knock_other_pitch` | `1.0` | Pitch for the non-wood knock sound. |

Negative volume/pitch values become `0`. Extremely large finite values remain possible up to `Float.MAX_VALUE`; use practical Bukkit sound ranges. `NaN` propagates through `Math.min/Math.max` as `NaN`, so do not use non-finite values.

There are no configurable supported-material lists, pairing offsets, sound names, permission requirements, world filters or interaction modes.

## Commands and permissions

BetterDoors registers no command and checks no permission.

Every player whose uncancelled main-hand interaction reaches the listener receives the same behaviour. Protection plugins remain authoritative only insofar as they cancel the original `PlayerInteractEvent` before `MONITOR`.

## Door normalization

All pairing operations first normalize an arbitrary clicked/powered door half to its bottom block:

1. require the block data to implement `Door`;
2. if `Door#getHalf()` is `BOTTOM`, use the block directly;
3. otherwise inspect the block below;
4. require that below block to also contain `Door` block data.

The implementation does not explicitly verify the below block has the same material/facing as the top before returning it; it relies on normal two-block door integrity.

## Double-door pairing algorithm

Starting from the normalized bottom half, the handler reads:

- facing: north, south, east or west;
- hinge: left or right.

It calculates one perpendicular neighbor:

| Facing | Left hinge neighbor | Right hinge neighbor |
|---|---|---|
| North/South | West | East |
| East/West | South | North |

The candidate is accepted only when:

1. candidate block data is `Door`;
2. candidate material exactly equals the primary door material;
3. candidate facing exactly equals the primary facing;
4. candidate hinge is opposite;
5. candidate can be normalized to a bottom half.

This intentionally excludes:

- mixed-material double doors;
- doors facing opposite directions;
- same-hinge adjacent doors;
- diagonally or unusually spaced doors;
- trapdoors/fence gates;
- malformed top/bottom arrangements.

The algorithm finds at most one neighbor.

## Player right-click synchronization

Handler declaration:

```java
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
```

Conditions:

- interaction hand must be `EquipmentSlot.HAND` (off-hand duplicate ignored);
- action must be `RIGHT_CLICK_BLOCK`;
- clicked block must have `Door` block data.

The listener does not directly toggle either door. It schedules a lifecycle-owned one-time task for the next tick. That task:

1. rereads the primary bottom block data;
2. verifies it is still a door;
3. reads its final `isOpen()` state after vanilla/other synchronous processing;
4. applies the same open state to the paired bottom block.

The neighbor lookup is performed before scheduling, while the final primary state is read inside the task. If the paired block is broken/replaced during the delay, `setDoorOpen` safely returns when its block data is no longer a door.

`Block#setBlockData(data, true)` applies physics when changing the paired door.

### Protection ordering

Because the listener runs at `MONITOR` and ignores cancelled interactions, an earlier protection cancellation prevents scheduling. However, the next-tick neighbor write is a direct block-data mutation and does not fire a second player interaction permission check. Protection plugins that permit opening one door but protect the adjacent paired door do not receive an independent BetterDoors-specific authorization callback.

## Redstone synchronization

Handler declaration:

```java
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
```

`BlockRedstoneEvent` is inspected only when `event.getBlock()` itself has `Door` block data.

The listener converts current values to booleans:

```text
wasPowered = oldCurrent > 0
nowPowered = newCurrent > 0
```

When the boolean powered state changes, the neighbor is mirrored immediately to `open = nowPowered`.

Important limits:

- It does not schedule next-tick reading of the primary door for redstone.
- It assumes powered means open and unpowered means closed.
- It does not inspect power on blocks adjacent to the door unless Paper fires the event with the door block itself.
- It does not synchronize when current changes between two positive values or two zero/non-positive values.
- It does not track multiple redstone sources or preserve a manually open state when power is removed.

Direct `setBlockData(..., true)` may trigger physics/redstone updates, but the listener avoids recursive changes when the paired door receives no boolean current transition event or is already in the requested state.

## Knock interaction

Handler declaration:

```java
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
```

Conditions:

- main hand only;
- `LEFT_CLICK_BLOCK`;
- clicked block's block data implements `Door`.

Material classification uses `Tag.WOODEN_DOORS`.

### Wooden door sound

- sound: `Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR`;
- category: `SoundCategory.BLOCKS`;
- location: clicked block center;
- configured wood volume/pitch.

### Other door sound

- sound: `Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR`;
- category: `SoundCategory.BLOCKS`;
- location: clicked block center;
- configured other volume/pitch.

The sound is played for both top and bottom clicked halves at that half's block center. It is audible according to Bukkit sound propagation and the recipient's block sound settings.

The feature does not cancel block damage. In survival, holding a tool and left-clicking begins normal break behaviour while also producing the knock sound, unless another plugin cancels the interaction.

## Event ordering summary

All three handlers run at `MONITOR` and `ignoreCancelled = true`.

Bukkit convention treats `MONITOR` as observation-only; BetterDoors' right-click listener schedules a later mutation and its redstone listener directly mutates the paired block from `MONITOR`. Other plugins should be aware that the final paired-door state can change after the original event processing.

Within the same tick:

- knock sound happens during interaction processing;
- redstone mirroring happens immediately;
- player right-click mirroring happens on the next lifecycle tick.

## State, persistence and messaging

BetterDoors holds only immutable sound settings and a feature reference. It has no per-door or per-player map.

It does not use:

- DataProvider/database entities;
- Redis or plugin messaging;
- configuration-generated data files;
- APIs/services;
- PlaceholderAPI;
- recurring tasks.

Door state remains ordinary world block data and is persisted by Minecraft/Paper's world saving.

## Lifecycle

Initialization:

1. construct `BetterDoorsHandler` and snapshot the four sound settings;
2. register one `BetterDoorsListener`.

Disable has no explicit body. Framework cleanup unregisters the listener and cancels any feature-owned next-tick mirror tasks that have not run.

Changing sound config requires reloading/re-enabling the feature because settings are copied into final handler fields.

## Developer source map

- Defaults/lifecycle: `features/betterdoors/BetterDoors.java`
- Pairing, state mutation and sounds: `features/betterdoors/internal/BetterDoorsHandler.java`
- Event wiring: `features/betterdoors/listener/BetterDoorsListener.java`
- Metadata: `features/betterdoors/meta/Meta.java`

## Operational verification

1. Build valid same-material double doors in all four facings and both hinge arrangements.
2. Right-click either half/door and verify the pair matches one tick later.
3. Test mixed materials, same hinges, opposite facings and malformed doors; verify they are not paired.
4. Cancel `PlayerInteractEvent` in a protection region and verify neither mirror nor knock occurs.
5. Test a primary allowed/neighbor protected boundary and decide whether the direct paired mutation is acceptable.
6. Power/unpower each door through buttons, plates, levers and redstone components; verify events whose block is the door synchronize immediately.
7. Change redstone strength between positive values and confirm no redundant mirror occurs.
8. Left-click wood, iron and copper doors and verify sound family/volume/pitch.
9. Test off-hand interaction to ensure duplicate events are ignored.
10. Break/replace a paired door in the one-tick window and verify the delayed task fails safely.
11. Reload/disable immediately after clicking and verify lifecycle cancellation prevents stale callbacks.

## Troubleshooting

- **Doors do not pair:** verify exact same material, same facing, opposite hinges and correct perpendicular adjacency.
- **Only one door changes with redstone:** Paper may fire `BlockRedstoneEvent` for the power source/adjacent block rather than the door, or the build does not satisfy pairing rules.
- **A protected neighboring door changes:** BetterDoors trusts cancellation of the original interaction and does not perform a second region check for the paired block.
- **Knock sound is missing:** check that the interaction is uncancelled, main-hand, left-click-block, and the block data is `Door`.
- **Copper door uses iron sound:** expected; only Bukkit-tagged wooden doors use the wooden sound, every other door uses the iron-door attack sound.
- **Config changes have no effect:** sound values are read once during handler construction; reload/re-enable the feature.
- **Trapdoors/fence gates are unaffected:** intentional; the implementation supports only `org.bukkit.block.data.type.Door`.
