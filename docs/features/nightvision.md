# NightVision

> Paper · Feature name `NightVision` · feature package `features.nightvision` · disabled by default

NightVision registers one player-only toggle command. It treats an infinite-duration Night Vision effect as “enabled”; toggling off removes the current Night Vision effect, while toggling on first removes any existing finite/other Night Vision effect and replaces it with an infinite amplifier-0 effect without ambient particles.

There is no persistence, join listener, ownership marker, world policy, target-player mode, database, Redis, API or PlaceholderAPI expansion.

## Command and permission

Actual root: `/nightvision`, no alias `/nv` in command metadata despite source comments/permission naming.

| Syntax | Permission | Sender | Behaviour |
|---|---|---|---|
| `/nightvision` | `serverfeatures.feature.nightvision.command.nv` | Player only | Toggle infinite Night Vision. All arguments are ignored; command still toggles. |

Console receives `general.player_command`. Tab completion is always empty.

Permission denial uses shared `general.no_permission_rank` with hard-coded `{rank}` `&3Supreme`.

## Complete configuration reference

File: `plugins/ServerFeatures/features/NightVision/config.yml`.

| Key | Default | Meaning |
|---|---:|---|
| `enabled` | `false` | Registers `/nightvision`. |

There are no configurable duration, amplifier, persistence, worlds, particles, icon, ambient flag, aliases, target player, messages or interaction rules. Previous documentation suggesting these options was inaccurate.

## Toggle algorithm

### Disable branch

The feature considers Night Vision owned/enabled when:

```text
player.hasPotionEffect(NIGHT_VISION)
and player.getPotionEffect(NIGHT_VISION).duration == PotionEffect.INFINITE_DURATION
```

It does not check amplifier, ambient, particles, icon or source. Any infinite Night Vision effect from another plugin/command/beacon-like system is treated as this feature's toggle and removed.

After removal, player receives `nightvision.status` with `{status}` = `&cuitgeschakeld`.

### Enable branch

When no infinite effect exists:

1. if any finite/other Night Vision exists, remove it completely;
2. construct:

```java
new PotionEffect(
  PotionEffectType.NIGHT_VISION,
  PotionEffect.INFINITE_DURATION,
  0,
  false,
  false
)
```

3. add it to player;
4. send status with `{status}` = `&aingeschakeld`.

The selected constructor sets ambient false and particles false. Icon behaviour follows the specific Bukkit constructor overload/default for the deployed API (the five-argument overload does not explicitly specify icon).

`Player#addPotionEffect` return value is ignored. A plugin blocking/replacing the effect can cause success feedback even when final state differs.

## Ownership and interaction with other effects

NightVision does not track which effect it created.

Consequences:

- a finite high-amplifier or plugin-managed Night Vision is destroyed when player toggles on;
- any infinite Night Vision, regardless of source/amplifier, is destroyed when toggling off;
- another plugin can replace/remove the infinite effect; command status is recalculated from current effect on next use;
- milk/death/server mechanics may remove the effect according to Bukkit/Paper behaviour;
- no reapplication task prevents flicker/removal;
- no “restore previous effect” snapshot exists.

If source ownership matters, persist a feature marker/state and reconcile only the effect created by this command.

## Persistence and lifecycle

There is no state map or persistent preference. The effect itself is ordinary player potion-effect state:

- whether it survives death/logout/server restart follows Paper/playerdata potion-effect persistence and other plugin behaviour;
- feature enable does not inspect/reapply players;
- feature disable does not remove infinite effects;
- permission removal does not remove an already active effect;
- backend switch carries only what the server/proxy/playerdata transfer normally preserves, not a feature message.

Because Paper normally saves active potion effects in playerdata, an infinite effect can remain after disabling/uninstalling the feature until manually removed or normal game/plugin action clears it.

## Messages and variables

| Key | Variables | Use |
|---|---|---|
| `nightvision.status` | `{status}` | Toggle feedback. Code supplies preformatted Dutch `&aingeschakeld` or `&cuitgeschakeld`. |

Status values are hard-coded rather than separate localization keys, reducing translation flexibility.

## Event, threading and performance

No listeners/tasks are registered. Command runs synchronously on server thread and performs constant-time potion-effect operations.

Arguments are not validated. `/nightvision anything` toggles exactly like `/nightvision`.

## Persistence/database/messaging summary

- DataProvider/database: none.
- Redis/proxy messaging: none.
- API: none.
- PlaceholderAPI: none.
- Durable input: only feature/localization config.
- Durable runtime outcome: ordinary Paper potion effect may be saved in playerdata.

## Lifecycle

Initialization registers one feature command. Disable is empty; lifecycle unregisters command only.

## Developer source map

- Defaults/messages/lifecycle: `features/nightvision/NightVision.java`
- Permission/toggle/effect creation: `features/nightvision/command/NightVisionCommand.java`
- Metadata: `features/nightvision/meta/Meta.java`

## Operational verification

1. Verify exact `/nightvision` root and lack of `/nv` alias.
2. Test permission/player-only behaviour and ignored arguments.
3. Toggle on/off and inspect duration/amplifier/ambient/particles/icon.
4. Apply finite/infinite Night Vision with different amplifiers/sources and test destructive replacement/removal.
5. Have another plugin deny/replace `addPotionEffect`; compare feedback with final effect.
6. Test death, milk, logout/rejoin, backend switch and server restart persistence.
7. Remove permission/disable feature while active and verify effect remains.
8. Test conflict with potion-management plugins.

## Troubleshooting

- **`/nv` does not work:** no alias is registered; use `/nightvision`.
- **Existing Night Vision was removed:** toggle-on removes any current finite/non-infinite effect; toggle-off removes any infinite effect.
- **Effect remains after disabling feature:** disable has no cleanup and playerdata may persist it.
- **Config duration/amplifier changes do nothing:** those settings do not exist; values are hard-coded.
- **Success message but no effect:** add result is ignored and another plugin may override it.
- **Arguments unexpectedly accepted:** command ignores argument count/content.
