# Teleportation

> Paper · Feature ID `teleportation` · disabled by default · commands `/randomtp` (`/rtp`) and `/tppos`

Teleportation provides two self-service commands on the player's current backend world:

- random coordinate sampling inside configured/world-border bounds, excluding an optional inner rectangle and GriefPrevention claims;
- integer coordinate teleportation that resolves a safe feet position at or below the requested Y.

Both commands reserve independent in-memory cooldowns, execute world access on the main task path, record an Essentials `/back` location where available, zero velocity, teleport synchronously and optionally play two sounds.

There is no warm-up, movement/damage cancellation, economy cost, world argument, other-player target or persistent cooldown.

## Commands and permissions

| Syntax | Permission | Behavior |
|---|---|---|
| `/randomtp` | `serverfeatures.feature.teleportation.command.randomtp` | Search current world for a random destination. Alias `/rtp`. Player-only; accepts no arguments. |
| `/tppos <x> <y> <z>` | `serverfeatures.feature.teleportation.command.tppos` | Resolve and teleport within the player's current world. Player-only; exactly three Java integers. |

Additional permissions:

| Permission | Effect |
|---|---|
| `serverfeatures.feature.teleportation.bypass.cooldown` | Skips cooldown read/write for either command. |
| `serverfeatures.feature.teleportation.bypass.worldborder` | Skips the complete `/tppos` `effectiveOuter` check—including both WorldBorder and configured outer rectangle. It does not affect random teleport sampling. |

There is no permission bypass for inner bounds, unsafe blocks or GriefPrevention claims, and no granular staff-target syntax.

Both commands use internal permission checks rather than a command-meta permission. They return no tab completions.

## Configuration

### Bounds

```yaml
bounds:
  inner:
    min_x: 0
    max_x: 0
    min_z: 0
    max_z: 0
  outer:
    min_x: 0
    max_x: 0
    min_z: 0
    max_z: 0
respect_world_border: true
```

Coordinates accept numeric values or parseable integer strings through `TeleportBounds`. Minimum/maximum values are normalized independently, so reversed endpoints are valid.

#### Inner rectangle

`0,0,0,0` disables the inner rectangle. Otherwise it is a reserved rectangle excluded from **random teleport only**. `/tppos` does not check the inner rectangle.

#### Configured outer rectangle

`0,0,0,0` disables the configured outer rectangle. When an inner rectangle exists, the outer rectangle is used only if it fully encloses the normalized inner rectangle. Otherwise the configured outer is silently ignored.

#### WorldBorder

When `respect_world_border=true`, the effective outer rectangle is the integer rectangle intersection of:

- the world's current WorldBorder bounds;
- the valid configured outer rectangle, or a hard-coded `-30,000,000..30,000,000` rectangle when no configured outer exists.

WorldBorder edges are calculated with floor(center − half size) and ceil(center + half size), making the integer rectangle potentially include edge block coordinates whose centers need separate vanilla border consideration.

When `respect_world_border=false`, only the configured outer/infinite hard limit is used.

An empty intersection makes random teleport fail and non-bypassed `/tppos` reject every coordinate.

### Safety and attempts

| Key | Default | Meaning |
|---|---:|---|
| `disabled_blocks` | `LAVA`, `WATER`, `LILY_PAD`, `CACTUS` | Materials rejected by safety checks. Invalid names are silently discarded. |
| `randomtp.max_attempts` | `250` | Maximum random samples. Not clamped; zero/negative means no samples. |
| `randomtp.y_offset_after_highest` | `4.0` | Y offset added above `World#getHighestBlockAt`. |
| `play_sounds` | `true` | Plays hard-coded Enderman teleport and Ender Dragon flap sounds after success. |

If `disabled_blocks` is not a list, the hard-coded default set is used. If it is a list containing only invalid names, the effective disabled set is empty.

### Cooldowns

| Key | Default |
|---|---:|
| `cooldown_seconds.randomtp` | `10` |
| `cooldown_seconds.tppos` | `10` |

Cooldown values are read dynamically as numbers and otherwise fall back to `10`. Values `<=0` disable that action's cooldown.

State is keyed by `(player UUID, TeleportAction)` and stored as last-use epoch milliseconds. RandomTP and TPPos cooldowns are independent.

## Cooldown ordering

A non-bypassed command calls `tryStart` before bounds/safety work:

1. calculate remaining whole seconds;
2. reject and message when positive;
3. otherwise store current timestamp immediately.

The reservation is reset when:

- `/tppos` is outside effective outer bounds;
- no safe random location is found;
- no safe `/tppos` location is found;
- `Player#teleport` returns false;
- `performTeleport` throws.

It remains after success.

There is no quit listener in this feature. Cooldown entries remain in the local map across disconnect/reconnect during the same feature lifetime and expire by time. Disable/reload clears all entries.

Remaining seconds use truncated elapsed seconds, so user-visible timing is coarse.

## Random teleport bounds sampling

`SafeLocationFinder.findRandomSafeLocation(currentWorld)`:

1. calculate effective outer rectangle;
2. subtract the intersection with inner rectangle into up to four non-overlapping bands;
3. compute each band's inclusive integer area;
4. choose a band weighted by area;
5. choose inclusive random X/Z in that band;
6. hard-check outer and inner again;
7. obtain `world.getHighestBlockAt(x,z)`;
8. reject when that block or block below has a disabled material;
9. reject any GriefPrevention claim;
10. return block center at `highest location + (0.5, configuredYOffset, 0.5)`, clamped to world height.

The weighted band construction avoids repeatedly sampling the reserved inner area, even when it occupies most of the outer rectangle.

### RandomTP safety limits

The random path does **not** perform the same feet/head/solid-ground validation used by `/tppos`. It assumes the highest-block result plus Y offset is suitable after material/claim checks.

Consequences:

- non-solid/passable highest materials not in `disabled_blocks` can be accepted;
- the player is normally placed four blocks above the highest block and falls;
- overhead structures/caves are not considered after adding the offset;
- the final location can be near the height clamp;
- `getHighestBlockAt` can synchronously load/generate sampled chunks;
- up to `max_attempts` samples occur in one main-thread task.

A very large default/infinite area can therefore trigger random chunk generation far from existing terrain and cause a visible tick spike.

## GriefPrevention behavior

The hook captures a GriefPrevention instance at `SafeLocationFinder` construction. RandomTP considers any claim unsuitable regardless of owner/trust.

Claim lookup failures are swallowed and treated as “not in a claim” (fail open). When GriefPrevention is absent, all locations pass this check.

`/tppos` does not check claims at all.

## `/tppos` coordinate resolution

Arguments must be decimal Java integers. Relative (`~`), local (`^`), floating-point coordinates and a world argument are unsupported.

Before scheduling safety search, non-bypassed actors must have X/Z inside `effectiveOuter` for the target's current world. The permission name says worldborder, but bypass skips configured outer bounds too.

`findSafeForTpPos(world,x,y,z)` computes:

- `surfaceFeetY = highestBlockYAt(x,z) + 1`;
- feet/head/ground safety using the disabled material set.

### When requested Y is at or above surface feet

It ignores the exact requested Y, clamps `surfaceFeetY`, checks only that surface feet position and returns it or fails. `/tppos 100 300 100` therefore teleports to the surface rather than Y=300.

### When requested Y is below surface feet

It scans downward from clamped requested Y through `minHeight + 1` and returns the first feet position where:

- block below is solid;
- block below is not passable;
- ground material is not disabled;
- feet and head blocks are air or passable non-liquid blocks.

It never scans upward from a blocked requested underground position and never searches adjacent X/Z.

Passable but harmful/non-air blocks at feet/head can be accepted unless liquid; disabled materials are checked only for the ground block in this method.

Claims and inner reserved bounds are not considered.

## Teleport execution ordering

After a destination is found:

1. record a cloned local fallback back location;
2. ask Essentials to set its last location, if captured;
3. set player velocity to `(0,0,0)`;
4. call synchronous `Player#teleport(destination)`;
5. on success, play optional sounds;
6. send success message.

Back location is recorded **before** knowing whether teleport succeeds. A false/throwing teleport can therefore update Essentials `/back` despite no successful move. Velocity is also already zeroed and is not restored on failure.

The local `BackService` map has no getter or `/back` command; it is currently write-only fallback state. Essentials is the only effective exposed `/back` integration.

The service creates an Essentials hook directly, while the anonymous fallback service also creates an unused Essentials hook. Plugin availability is captured at construction and errors from Essentials are swallowed.

## Scheduled task and player lifecycle

Both commands schedule a one-time main task even though command execution already normally occurs on the main thread. The callback accesses target world/player without re-resolving UUID or checking online state.

A player can disconnect or change worlds between command invocation and callback:

- random search uses `target.getWorld()` at callback time;
- `/tppos` performs the pre-check against invocation-time current world, then callback uses callback-time current world;
- if the player switched worlds, the earlier bounds decision can apply to a different world than the safety search/teleport;
- actor/target object messages and teleport calls depend on Bukkit object behavior after disconnect.

There is no operation generation token, in-flight flag or command coalescing. Cooldown usually prevents repeated self attempts, but bypass users can start multiple operations and later completion order wins.

## Sound effects

On success and when enabled, the destination player hears:

- `ENTITY_ENDERMAN_TELEPORT`, volume `10`, pitch `1.5`;
- `ENTITY_ENDER_DRAGON_FLAP`, volume `10`, pitch `1.5`.

Sound errors are swallowed. Sounds are not configurable individually and no origin sound/particles are used.

## Messages

| Key | Variables/use |
|---|---|
| `teleportation.usage.randomtp` | Arguments supplied to `/randomtp`. |
| `teleportation.usage.tppos` | Wrong argument count. |
| `teleportation.working.randomtp` | After cooldown accepted, before search. |
| `teleportation.working.tppos` | After bounds accepted, before safe search. |
| `teleportation.success.randomtp` | Successful teleport. |
| `teleportation.success.tppos` | Successful teleport. |
| `teleportation.cooldown_active` | `{seconds}`. |
| `teleportation.error.internal` | Teleport false/exception. |
| `teleportation.randomtp.no_safe_found` | `{attempts}` configured value. |
| `teleportation.tppos.coords_invalid` | Non-integer input. |
| `teleportation.tppos.outside_worldborder` | Effective outer rejection. |
| `teleportation.tppos.not_safe` | No safe feet position. |

All messages go to the actor; actor and target are currently the same player in both registered commands.

## Persistence, events and APIs

Teleportation has no database, Redis, plugin messaging, PlaceholderAPI expansion or Bukkit custom teleport event of its own. Bukkit's normal teleport event is produced by `Player#teleport` and other plugins can cancel it, causing the boolean false path where supported.

`getService()` and `getState()` expose concrete objects from the feature instance but neither is registered through the API manager.

## Performance and safety boundaries

- RandomTP is synchronous and may load/generate up to `max_attempts` chunks.
- No time budget, chunk-existing requirement or per-tick batching exists.
- Bounds can span the vanilla hard limit by default.
- Area arithmetic uses long and the largest 60,000,001² area remains within long.
- Disabled materials and config bounds are re-read for operations, while integration hooks are captured at initialization.
- Random safety is less strict than TPPos safety.
- Claim lookup fails open.
- `/tppos` can access/generate chunks at arbitrary bypassed X/Z within Minecraft integer/API limits.

## Important implementation boundaries

- Self/current-world commands only.
- No `/tppos ... [world]` argument.
- No warm-up, cost or movement/damage cancellation.
- Inner bounds affect RandomTP only.
- Border bypass also bypasses configured outer bounds.
- Cooldowns survive reconnect during the same feature lifetime.
- RandomTP may place players four blocks above ground and does not validate feet/head.
- `/tppos` above-surface Y resolves to surface, not requested Y.
- `/tppos` underground scans down only.
- RandomTP excludes all GP claims; TPPos excludes none.
- Local back map is write-only.
- Back/velocity mutate before teleport success.
- Scheduled callbacks do not revalidate online/world generation.
- No concurrent-operation token exists.

## Verification checklist

1. Test default zero bounds with WorldBorder on/off and inspect effective rectangles.
2. Configure reversed endpoints, invalid outer-not-enclosing-inner and empty intersections.
3. Confirm RandomTP never lands inside inner bounds and `/tppos` can.
4. Test world-border bypass outside both WorldBorder and configured outer.
5. Profile main-thread time/chunk generation for large bounds and 250 attempts.
6. Test disabled/invalid material lists and highest blocks such as leaves, snow, water, cactus and structures.
7. Test GriefPrevention wilderness/claim and simulated lookup failure.
8. Exercise `/tppos` above surface, exactly surface, underground blocked, liquid and passable blocks.
9. Disconnect/change world between invocation and scheduled callback.
10. Cancel Bukkit teleport with another plugin and inspect back location/velocity/cooldown reset.
11. Test Essentials present/absent/reloaded and `/back` result.
12. Verify cooldown independence, bypass, failure reset, reconnect and feature reload.

## Source map

- Defaults/messages/service composition: `features/teleportation/Teleportation.java`
- Commands: `features/teleportation/command/RandomTpCommand.java`, `TpPosCommand.java`
- Cooldown state/actions: `features/teleportation/internal/TeleportState.java`, `TeleportAction.java`
- Core flow/back/velocity/teleport: `features/teleportation/service/TeleportService.java`
- Rectangles/WorldBorder: `features/teleportation/service/TeleportBounds.java`
- Random and TPPos safety: `features/teleportation/service/SafeLocationFinder.java`
- Back state: `features/teleportation/service/BackService.java`
- Sounds: `features/teleportation/service/TeleportEffects.java`
- Integrations: `features/teleportation/integration/EssentialsHook.java`, `GriefPreventionHook.java`
