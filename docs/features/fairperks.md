# FairPerks

FairPerks provides native ServerFeatures implementations of `/fly`, `/god`, and the optional double-sneak god macro. It owns all live flight and god state itself.

FairPerks has a feature dependency on the native `CombatTag` feature. CombatLogX is not used or supported.

## Responsibilities

The feature owns:

- session and persistent fly preferences;
- session and persistent god-mode preferences;
- native Bukkit flight capability ownership;
- god-mode damage cancellation;
- permission cleanup when a player logs in;
- global and per-perk world and game-mode policy;
- CombatTag and nearby-hostile activation guards;
- PvP, hostile-mob, targeting, explosive, ignition, lava, and tamed-pet restrictions;
- direct and indirect player-action attribution.

The feature is disabled by default.

## Commands

| Command | Description |
| --- | --- |
| `/fly [on|off|toggle|status]` | Manage personal flight. |
| `/fly <player> [on|off|toggle|status]` | Manage an online player's flight. |
| `/god [on|off|toggle|status]` | Manage personal god mode. |
| `/god <player> [on|off|toggle|status]` | Manage an online player's god mode. |
| `/godmacro [on|off|toggle|status]` | Manage the double-sneak god toggle. |
| `/fairperks inspect <player>` | Show authoritative runtime state. |

A player whose permission is removed during a session can still disable an active personal perk. Permission removal is otherwise reconciled on the player's next login; there is no repeating permission scan.

## Permissions

| Permission | Purpose |
| --- | --- |
| `serverfeatures.feature.fairperks.fly.use` | Use personal flight. |
| `serverfeatures.feature.fairperks.fly.others` | Manage another online player's flight. |
| `serverfeatures.feature.fairperks.fly.persist` | Restore desired flight after reconnecting. |
| `serverfeatures.feature.fairperks.fly.bypass-activation` | Bypass combat and hostile activation guards. |
| `serverfeatures.feature.fairperks.god.use` | Use personal god mode. |
| `serverfeatures.feature.fairperks.god.others` | Manage another online player's god mode. |
| `serverfeatures.feature.fairperks.god.persist` | Restore desired god mode after reconnecting. |
| `serverfeatures.feature.fairperks.god.bypass-activation` | Bypass combat and hostile activation guards. |
| `serverfeatures.feature.fairperks.godmacro.use` | Configure and use the god macro. |
| `serverfeatures.feature.fairperks.restrictions.bypass` | Bypass action, pet, and targeting restrictions. |
| `serverfeatures.feature.fairperks.admin.inspect` | Use `/fairperks inspect`. |

Permission names are fixed in code.

## Combat integration

Enabling fly or god is blocked when the player is tagged by the native `CombatTagApi`, unless the relevant FairPerks activation-bypass permission applies.

Feature dependency metadata ensures CombatTag loads first. Reloading CombatTag cascades through FairPerks, so FairPerks never retains a stale service reference. The old availability fallback and CombatLogX reflection bridge have been removed.

Disabling a perk remains allowed during combat.

## Tamed pets

When `restrictions.tamed-pet-damage` is enabled, pets owned by a player with effective god mode or active FairPerks flight cannot damage another entity. This applies to direct pet damage and supported indirect pet damage chains such as projectiles.

The owner's restriction bypass permission applies. Damage against the owner is not blocked by this rule.

## Configuration

```yaml
commands:
  fly-aliases: []
  god-aliases: []
  godmacro-aliases: []

worlds:
  mode: ALL
  values: []

flight:
  enable-starts-flying: true
  allowed-game-modes: [SURVIVAL, ADVENTURE]
  worlds:
    mode: BLACKLIST
    values: []
  persistence:
    enabled: true
    restore-active-flight: true
    restore-when-airborne: true
  revocation:
    cancel-next-fall-damage: true

god:
  allowed-game-modes: [SURVIVAL, ADVENTURE, CREATIVE, SPECTATOR]
  worlds:
    mode: BLACKLIST
    values: []
  persistence:
    enabled: true
  damage:
    protect-void: false

activation-guard:
  combat:
    enabled: true
  hostile-nearby:
    enabled: true
    horizontal-radius: 16
    vertical-radius: 16

restrictions:
  pvp: true
  tamed-pet-damage: true
  hostile-melee: true
  hostile-projectiles: true
  hostile-targeting: true
  exploding-beds: true
  exploding-anchors: true
  end-crystals: true
  tnt-prime: true
  tnt-ignite: true
  creeper-ignite: true
  lava-near-hostiles: true
  block-ignite-near-hostiles: true
  nearby-radii:
    ignite: 5
    lava: 5
    tnt: 10
  block-ignite-causes: [FLINT_AND_STEEL, FIREBALL]

hostiles:
  include: []
  exclude: []
  spawner-mobs-exempt: true
  mark-spawner-mobs: false

god-macro:
  enabled: true
  interval-millis: 350

feedback:
  actionbar-cooldown-millis: 1000
```

The global and per-perk world modes accept `ALL`, `BLACKLIST`, or `WHITELIST`. World names are matched case-insensitively. Empty game-mode sets and invalid enum values fail feature startup.

## Acceptance checklist

- Enable and disable fly and god in every configured game mode.
- Verify CombatTag blocks activation and the activation-bypass permissions work.
- Verify direct, projectile, TNT, cloud, fangs, firework, and pet restrictions.
- Verify a protected owner's wolf cannot damage players, neutral mobs, or hostile mobs.
- Verify a pet can damage its owner and the restriction bypass works.
- Reconnect while flying and test permission-loss cleanup.
- Enter blocked global and per-perk worlds.
- Reload CombatTag while FairPerks state is active and confirm both features restore cleanly.
- Verify no CombatLogX plugin, class, soft dependency, documentation, or runtime lookup remains.
