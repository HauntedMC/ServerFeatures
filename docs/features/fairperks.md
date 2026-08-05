# FairPerks

FairPerks provides native ServerFeatures implementations of `/fly`, `/god`, and the optional double-sneak god macro. It owns all live fly and god state itself.

## Responsibilities

The feature owns:

- session and persistent fly preferences;
- session and persistent god-mode preferences;
- native Bukkit flight capability ownership;
- god-mode damage cancellation;
- permission cleanup when a player logs in;
- world and game-mode suspension/restoration;
- combat and hostile-mob activation guards;
- PvP, hostile-mob, targeting, explosive, ignition, and lava restrictions;
- direct and indirect player-action attribution.

The feature is disabled by default.

## Commands

| Command | Description |
| --- | --- |
| `/fly [on|off|toggle|status]` | Manage personal flight. Running `/fly` toggles it. |
| `/fly <player> [on|off|toggle|status]` | Manage an online player's flight. |
| `/god [on|off|toggle|status]` | Manage personal god mode. Running `/god` toggles it. |
| `/god <player> [on|off|toggle|status]` | Manage an online player's god mode. |
| `/godmacro [on|off|toggle|status]` | Manage the double-sneak god toggle. |
| `/fairperks inspect <player>` | Show the authoritative runtime state for diagnostics. |

A player whose permission is removed during a session can still disable an already-active personal perk. Permission removal is otherwise reconciled on the player's next login; there is intentionally no repeating permission scan.

Staff with a perk's `*.others` permission can grant or remove that perk for an online player's current session even when the target lacks the personal use permission. Such an administrative grant is not persisted unless the target independently has both the use and persist permissions.

The feature treats its command roots as required infrastructure. It refuses to finish loading when a required root or configured alias cannot be registered.

## Permissions

| Permission | Purpose |
| --- | --- |
| `serverfeatures.feature.fairperks.fly.use` | Use personal flight. |
| `serverfeatures.feature.fairperks.fly.others` | Inspect or change flight for another online player. |
| `serverfeatures.feature.fairperks.fly.persist` | Restore desired flight after reconnecting. |
| `serverfeatures.feature.fairperks.fly.bypass-activation` | Bypass combat and aggressive-hostile activation guards for flight. |
| `serverfeatures.feature.fairperks.god.use` | Use personal god mode. |
| `serverfeatures.feature.fairperks.god.others` | Inspect or change god mode for another online player. |
| `serverfeatures.feature.fairperks.god.persist` | Restore desired god mode after reconnecting. |
| `serverfeatures.feature.fairperks.god.bypass-activation` | Bypass combat and aggressive-hostile activation guards for god mode. |
| `serverfeatures.feature.fairperks.godmacro.use` | Configure and use the double-sneak god toggle. |
| `serverfeatures.feature.fairperks.restrictions.bypass` | Bypass FairPerks action and targeting restrictions. |
| `serverfeatures.feature.fairperks.admin.inspect` | Use `/fairperks inspect`. |

Permission node names are intentionally fixed in code.

## Flight lifecycle

FairPerks distinguishes desired flight from active flight.

- `/fly on` records a desired state and grants `allowFlight` in permitted worlds and game modes.
- Restrictions apply only while the player is actually flying.
- Creative and spectator flight are native Minecraft capabilities and are never removed by FairPerks.
- When FairPerks grants flight, it records ownership so disabling the perk restores any pre-existing non-FairPerks capability instead of blindly clearing it.
- Persistent flight requires both the use and persist permissions.
- On login, a persistent flyer can be placed back into active flight when they logged out flying or join in mid-air, according to configuration.
- Active flight is captured before normal server shutdown or feature disable, before FairPerks revokes its live capability.
- If invalid FairPerks flight is removed while airborne, the next fall-damage event can be cancelled once. The grace is cleared one tick after a safe landing, or immediately on damage, death, or logout.

World and game-mode changes suspend effective flight without clearing the desired state. Returning to an allowed environment restores the capability. These environment reconciliations do not recheck permissions.

Players already online when the feature is enabled at runtime are initialized on the next server tick. During a feature reload, restored snapshots take priority and this bootstrap only initializes players missing from the snapshot. Commands also initialize their target idempotently before reading state, so they remain correct during that first-tick window.

## God mode

God mode is implemented through damage-event policy rather than a permanently saved invulnerability flag.

Enabling god mode does not:

- heal the player;
- refill hunger or saturation;
- clear potion effects;
- alter inventory or experience.

Ordinary damage is cancelled while effective god mode is active. Void protection is independently configurable and disabled by default. Returning to an allowed world or game mode also clears current hostile targets under the configured targeting policy.

## Activation guards

Enabling fly or god can be blocked when:

- CombatLogX reports the player in combat;
- CombatLogX is unavailable and the configured fallback denies activation;
- a hostile mob within the configured horizontal and vertical radii is currently targeting the player;
- the current world is blocked;
- the current game mode is blocked.

Disabling a perk is always allowed. CombatLogX is optional and resolved once when the feature starts. ServerFeatures declares it as a soft dependency so the hook is resolved after CombatLogX when both are installed.

## Fairness restrictions

The following restrictions can be enabled independently:

- player-versus-player damage while in effective god mode or active flight;
- direct and indirect damage against hostile mobs;
- hostile targeting of protected players;
- exploding beds outside the Overworld;
- exploding charged respawn anchors outside the Nether;
- damaging end crystals;
- priming or igniting TNT;
- igniting creepers;
- placing lava near hostile mobs;
- igniting blocks near hostile mobs.

Indirect attribution covers ordinary projectile shooters, projectile owner UUIDs, player-sourced TNT and area-effect clouds, evoker fangs, tamed mobs, and fireworks. This prevents changing the damage delivery mechanism from bypassing the same fairness policy.

Spawner-created hostile mobs can be exempt through the feature's own marker. Malformed markers are treated as absent rather than breaking event handling. Marker creation remains independently controlled by `hostiles.mark-spawner-mobs`.

The classifier follows Bukkit's `Enemy` hierarchy. Configured exclusions always win, and configured inclusions can add custom entity types. The activation guard only treats a classified mob as aggressive when its current Bukkit target is the player enabling the perk.

## Configuration

The feature-owned configuration contains these groups:

```yaml
commands:
  fly-aliases: []
  god-aliases: []
  godmacro-aliases: []

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
    allow-when-unavailable: true
  hostile-nearby:
    enabled: true
    horizontal-radius: 16
    vertical-radius: 16

restrictions:
  pvp: true
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

Aliases are trimmed, normalized to lowercase, validated against Bukkit command-label rules, and stored as immutable lists. Labels must be unique and must not conflict with another Bukkit or Brigadier command. FairPerks refuses to enable when `/fly`, `/god`, `/fairperks`, `/godmacro`, or a configured alias is unavailable.

World names are normalized case-insensitively. Empty game-mode sets and invalid enum values fail configuration loading instead of silently weakening policy.

## Acceptance checklist

- Enable and disable fly in survival and adventure mode.
- Verify creative and spectator flight remain native when FairPerks flight is disabled.
- Log out while flying and reconnect in mid-air with persistent permissions.
- Stop the server while a persistent player is flying and verify active flight restores after startup.
- Repeat without the use or persist permission and verify cleanup on login.
- Remove a permission while online and verify there is no automatic scan; confirm `/fly off` or `/god off` still works for the active state.
- Enable the feature while players are already online and verify they receive initialized state without reconnecting.
- Use the `*.others` command on a target without the personal use permission and verify the grant works only for that session.
- Enable god mode at low health and hunger and verify neither value changes.
- Verify melee, projectiles, owner-UUID projectiles, pets, fangs, fireworks, TNT, area effects, and PvP restrictions.
- Verify beds, anchors, crystals, TNT, creepers, lava, and block ignition.
- Verify spawner mobs remain exempt when configured.
- Reload the feature while a player is flying and while a player is in god mode.
- Verify fall grace survives the landing move event long enough to cancel the matching fall-damage event, and clears after a safe landing.
- Verify no duplicate commands, listeners, or tasks remain after reload.
