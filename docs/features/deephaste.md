# DeepHaste

> Paper · Feature name `DeepHaste` · feature package `features.deephaste` · disabled by default

DeepHaste replaces a beacon-applied Haste effect with a stronger fixed-duration effect while the player is below a configured Y threshold. A movement listener removes an effect whose amplifier exactly matches the configured boost after the player moves above the threshold.

It does not continuously search for nearby beacons, persist source information, check permissions/worlds, restore the original beacon amplifier, or distinguish its effect with metadata.

## Commands and permissions

No command or permission is registered. Every player affected by the relevant events is eligible.

## Complete configuration reference

File: `plugins/ServerFeatures/features/DeepHaste/config.yml`.

| Key | Default | Exact behaviour |
|---|---:|---|
| `enabled` | `false` | Enables Paper compatibility check and listener registration. |
| `y_level` | `6` | Beacon boost applies when `player.getLocation().getY() < y_level`. Removal applies only when Y is strictly `> y_level`. At exactly the configured value neither condition applies. Direct cast to `int` on every event. |
| `haste_amplifier` | `7` | Bukkit potion amplifier, where amplifier 0 is Haste I and 7 is Haste VIII. Used for replacement and exact removal identification. Direct cast on every event. |

There is no configurable duration: boosted effect duration is hard-coded to `320` ticks (16 seconds).

## Paper compatibility check

During initialization, the feature considers the server Paper when either:

- `Bukkit.getServer().getName().equalsIgnoreCase("Paper")`; or
- `Bukkit.getServer().getVersion().contains("Paper")`.

When the check fails, it logs a warning and returns from `initialize()` without registering the listener. The feature loader may still consider the feature enabled even though no behaviour was installed. Paper forks whose name/version does not contain `Paper` can be false negatives; compatible non-Paper-branded implementations are not detected by API presence.

The listener imports `com.destroystokyo.paper.event.block.BeaconEffectEvent`, so binary/API compatibility with the target Paper release is required.

## `BeaconEffectEvent` contract

```java
@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
```

Flow:

1. obtain event player;
2. read `y_level`;
3. require current player Y strictly below threshold;
4. require `event.getEffect().getType() == PotionEffectType.HASTE`;
5. read configured amplifier;
6. replace event effect with:

```java
new PotionEffect(PotionEffectType.HASTE, 320, configuredAmplifier)
```

The replacement constructor uses default ambient/particles/icon values from Bukkit's selected overload. It does not preserve the original effect's duration, amplifier, ambient state, particles, icon or source-level details.

Because the event is not cancelled, Paper continues applying the replaced effect. Events cancelled before `NORMAL` are ignored.

The feature boosts only effects delivered through `BeaconEffectEvent`. Haste from commands, potions, other plugins or existing player state is not upgraded merely because the player is deep underground.

## `PlayerMoveEvent` removal contract

```java
@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
```

On every accepted move event:

1. read current Haste effect;
2. when no Haste exists, return;
3. read current amplifier/configured amplifier;
4. if current Y is strictly above threshold **and** amplifiers are equal, call `player.removePotionEffect(HASTE)`.

### Identification limitations

The feature identifies its boosted effect only by amplifier equality. It does not track UUID/source/time.

Therefore it can remove:

- a command/plugin/potion Haste effect with the same amplifier;
- a newly reapplied beacon effect matching the configured amplifier;
- any other source indistinguishable by amplifier.

Conversely it will not remove a DeepHaste effect after config amplifier changes if the active effect retains the old amplifier.

Removal deletes the entire current Haste effect. It does not restore the underlying weaker beacon effect; Paper's next beacon pulse may apply a fresh normal effect depending on player range/Y and event flow.

### Threshold dead band

- Boost: Y `< y_level`.
- Remove: Y `> y_level`.
- Y exactly equal: no removal/boost action.

Minecraft locations are doubles. A player can cross between values without landing exactly, but the asymmetric comparisons should be intentional.

## Event ordering and interaction

Both listeners use `NORMAL`, so same-priority ordering with other plugins is unspecified.

- Another plugin at a later priority can replace the beacon effect after DeepHaste.
- Another plugin can cancel the beacon event after this listener, preventing application despite replacement.
- Movement removal occurs only when the move event is not already cancelled.
- The move handler reads `player.getLocation()` rather than `event.getTo()`, so it follows Bukkit's current player location at callback time.

No special handling exists for teleport, world change, respawn, death, quit, feature disable or beacon range loss.

## Runtime/performance

`PlayerMoveEvent` fires frequently. For every moving player the handler performs config reads and a potion-effect lookup. There is no location-block-change check, state cache or permission/world fast path.

This is normally small work, but config-service access on every movement should remain inexpensive. A future optimization could read validated immutable settings at initialization and only evaluate when Y crosses the threshold.

## Persistence, database and messaging

DeepHaste has no DataProvider/database, Redis, proxy messaging, API, PlaceholderAPI expansion or persistent player state. Potion effects remain ordinary Bukkit player state.

## Lifecycle

Initialization performs compatibility detection and, when accepted, listener registration. Disable is empty; lifecycle cleanup unregisters the listener.

Existing boosted effects are **not removed on feature disable**. They remain until duration expiry, another effect replacement/removal, or player state change. With 320-tick duration, the maximum normal residual is about 16 seconds.

Config changes are read on each event, except the Paper check. No explicit config reload command exists.

## Developer source map

- Defaults/Paper check/lifecycle: `features/deephaste/DeepHaste.java`
- Beacon replacement/movement removal: `features/deephaste/listener/BeaconEffectListener.java`
- Metadata: `features/deephaste/meta/Meta.java`

## Operational verification

1. Confirm listener initializes on the exact target Paper/fork build.
2. Stand below, exactly at and above `y_level` during beacon pulses.
3. Verify Haste only—not Speed/Resistance/etc.—is replaced.
4. Confirm default amplifier corresponds to Haste VIII and duration refreshes to 320 ticks.
5. Cross above threshold and verify matching-amplifier Haste is removed.
6. Apply non-beacon Haste with same/different amplifier and test removal collision.
7. Change amplifier while an old effect is active and observe identification mismatch.
8. Test cancelled beacon/movement events and other plugins at later priorities.
9. Disable feature while boosted and verify residual duration behaviour.
10. Profile move-event cost with many players.

## Troubleshooting

- **No boost:** verify Paper detection, `BeaconEffectEvent`, uncancelled event, Haste type and strict Y `<` threshold.
- **Effect disappears above cutoff:** intentional; matching amplifier is removed entirely, not downgraded.
- **Unrelated Haste is removed:** amplifier is the only ownership marker.
- **Boost remains after config change:** active effect may use old amplifier, so exact removal check no longer matches.
- **Fork supports event but feature warns non-Paper:** detection is name/version-string based.
- **Feature disabled but effect remains briefly:** disable does not clean active effects; hard-coded effect expires after up to 320 ticks.
