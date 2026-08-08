# PhantomToggle

> Paper · Feature name `PhantomToggle` · package `features.phantomtoggle` · disabled by default

PhantomToggle lets permitted players choose whether vanilla insomnia phantoms may spawn for them. The choice is stored locally in the player's Bukkit `PersistentDataContainer`; there is no database or network synchronization.

## Player behavior

The primary path listens to Paper's `PhantomPreSpawnEvent`, which exposes the exhausted entity the phantom is being spawned for. If that entity is a player who has disabled phantom spawning, the pre-spawn event is cancelled and the remaining phantom spawn attempt is aborted.

Paper documents pre-creature spawn events as an optimization hook with limited coverage, so the feature also listens to `CreatureSpawnEvent` as a fallback. The fallback only considers `NATURAL` phantom spawns with a non-null `Phantom#getSpawningEntity()` UUID and cancels the spawn when that UUID resolves to an opted-out player in the phantom's world.

This is deliberately scoped to vanilla insomnia phantom spawning for that player:

- phantoms spawned for other players are unaffected;
- already-existing phantoms are not removed;
- ordinary manually or plugin-spawned `CUSTOM` phantoms are not blocked;
- the player's `TIME_SINCE_REST` statistic is not changed or reset;
- disabling phantom spawning does not make the player immune to an existing phantom.

If the player later enables phantoms again, normal vanilla spawning resumes using their existing sleep/insomnia state.

## Command and permission

Permission:

```text
serverfeatures.feature.phantomtoggle.use
```

| Command | Behavior |
|---|---|
| `/phantomtoggle` | Toggles whether phantoms may spawn for the player. |
| `/phantomtoggle on` | Enables phantom spawning idempotently. |
| `/phantomtoggle off` | Disables phantom spawning idempotently. |
| `/phantomtoggle toggle` | Explicit toggle form. |
| `/phantomtoggle status` | Reports the current preference. |

The command is player-only and uses Paper's Brigadier command tree. Players without the use permission always retain normal vanilla phantom spawning, even if an old stored preference says `off`.

## Configuration

File:

```text
plugins/ServerFeatures/features/PhantomToggle/config.yml
```

| Key | Default | Meaning |
|---|---:|---|
| `enabled` | `false` | Loads the feature, listener and command. |
| `default-phantoms-enabled` | `true` | Preference used when a permitted player has not made an explicit choice. |

The default preserves vanilla behavior until a player explicitly disables phantom spawning. `default-phantoms-enabled` is captured when the feature initializes, so changing it requires a full feature reload; a soft reload only refreshes the underlying config and localization files.

## Persistence

The preference is stored in the player's Bukkit `PersistentDataContainer` under:

```text
serverfeatures:phantomtoggle_phantoms_enabled
```

The value is a byte: `1` means phantom spawning enabled and `0` means disabled. A missing, invalid or incompatible value falls back to `default-phantoms-enabled`.

Paper persists PDC with ordinary local playerdata, so the preference normally survives logout and restart. It is intentionally local to the Paper server and is not synchronized across different backend servers unless their playerdata is shared externally.

## Lifecycle and performance

The feature has no scheduler, database work or per-player polling. The fast path cancels `PhantomPreSpawnEvent` before a phantom entity is created, and `setShouldAbortSpawn(true)` prevents unnecessary follow-up attempts for the blocked player. `CreatureSpawnEvent` is only the correctness fallback recommended by Paper for cases where the pre-spawn optimization hook is skipped.

Framework cleanup unregisters the listeners and command when the feature is disabled. The preference service owns no external resources and remains valid until the feature instance itself is discarded, avoiding a temporary invalid state during cleanup.

## Verification

Automated tests cover:

- configured defaults with no stored preference;
- explicit PDC preferences overriding the default;
- permission loss restoring vanilla spawning behavior;
- invalid stored values falling back safely;
- PDC writes for explicit choices;
- cancelling and aborting a phantom pre-spawn for an opted-out player;
- leaving allowed and non-player pre-spawn cases untouched;
- cancelling a matching natural phantom through the `CreatureSpawnEvent` fallback;
- ignoring `CUSTOM` phantoms and natural phantoms without a spawning-player UUID.

In game, verify `/phantomtoggle off`, wait until the player is normally eligible for phantoms, and confirm no phantom is created for that player while another eligible player can still receive normal phantom spawns.
