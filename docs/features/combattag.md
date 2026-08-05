# CombatTag

CombatTag is the native ServerFeatures combat-state authority and has no external combat plugin dependency.

## Behavior

A player is tagged whenever a configured combat interaction deals positive final damage. Cancelled and zero-final-damage events are ignored.

- `PVP` tags player-versus-player interactions.
- `MOBS` tags player-versus-mob interactions in both directions.
- `BOTH` enables both policies.
- dealing or receiving another qualifying hit replaces the displayed opponent and resets the full timer;
- incoming hits also update the attacker retained for logout kill attribution;
- outgoing hits never overwrite a still-active incoming-attacker attribution;
- an outgoing-only tag deliberately has no invented logout attacker;
- if a displayed outgoing opponent dies while a different incoming attacker is retained, the session falls back to that incoming attacker instead of clearing combat;
- if the retained incoming attacker dies while another displayed opponent remains, only stale logout attribution is removed;
- a new tag sends the configured chat message;
- expiry or another configured untag cause sends the configured exit message;
- the remaining time is rendered through one bounded action-bar update task;
- unchanged action-bar frames are not resent;
- CombatTag uses an owner-scoped targeted override and can only clear the action bar while it still owns that override;
- tags are retained across a feature reload with only their actual remaining duration;
- tags are never persisted across a server restart.

The fixed bypass permission is:

```text
serverfeatures.feature.combattag.bypass
```

It defaults to operators. A bypassed player is not tagged, restricted, or punished. Runtime permission changes are reconciled by the display task, so granting the bypass also removes an active tag. The bypass does not prevent that player from being recorded as the opponent of a non-bypassed player they attack.

## Attribution

CombatTag uses Paper's authoritative damage source and resolves the responsible attacker rather than treating every damage carrier as an unrelated entity.

Supported attribution includes:

- direct players and mobs;
- projectiles and their shooter;
- tamed pets and their online owner;
- primed TNT and its source;
- fishing hooks;
- area-effect clouds;
- evoker fangs;
- fireworks;
- player-caused indirect explosions and other Paper damage sources when no more specific carrier applies.

Specific carrier rules take priority. For example, an ignored projectile remains ignored even when Paper reports its shooter as the causing entity, and disabling TNT linking is not bypassed by generic damage-source fallback.

Pet linking applies only to the attacking side. Attacking somebody else's pet does not tag the pet owner.

When projectile linking is disabled, every projectile, including fireworks, is ignored. When it is enabled, configured entity types are still ignored. The default ignored projectile types are `EGG`, `ENDER_PEARL`, and `SNOWBALL`.

Mobs created through configured spawn reasons do not tag players. The default exclusion is `SPAWNER`. CombatTag reads Paper's persistent entity spawn reason at damage time, so the rule remains correct across feature reloads, chunk unloads, and existing loaded entities without maintaining a separate UUID cache.

## Death and teleport lifecycle

By default:

- a player is untagged when they die;
- a tag is cleared when its only remaining opponent dies;
- simultaneous state is preserved when another current or retained incoming opponent still keeps the player in combat;
- creeper opponents are cleared when they begin exploding;
- Nether and End portals are blocked during combat;
- other teleports are blocked unless their cause is explicitly allowed;
- plugin, unknown, and ender-pearl teleports are allowed;
- allowed ender pearls do not reset the timer;
- an allowed teleport does not clear the tag.

The portal rule is evaluated independently from the general allowed-cause list, so adding a portal cause to that list does not silently defeat `prevent-portals`.

Entering a world outside the configured world rule clears the tag.

## Logout punishment

Logout punishment can independently:

- kill the quitting player;
- broadcast a localized message;
- execute any number of console commands.

When logout punishment is enabled, at least one of those actions must be configured. An enabled block that would do nothing fails configuration loading.

The most recent incoming attacker is supplied as the causing and direct entity of a Paper `GENERIC_KILL` damage source when that entity is still available. A direct health fallback guarantees the configured death even if another protection listener cancels or absorbs the attributed damage.

A player who has only dealt damage is still combat tagged and can still be punished, but no target is falsely registered as that player's killer. The unknown-attacker broadcast variant and empty attacker placeholders are used instead.

Administrative or server kicks are not punished by default. Set `punish-kicked-players: true` only when kicks should be treated exactly like voluntary disconnects.

Server shutdowns are never punished, independently of the kick setting. CombatTag checks Paper's explicit server-stopping state so scheduled restarts cannot kill players, broadcast combat-log messages, or execute logout commands.

Kill, broadcast, and command actions are failure-isolated. An exception or unknown console command is logged without preventing the remaining configured actions. Command placeholders are replaced in a single pass and every ISO control character is replaced before console dispatch.

Command placeholders:

| Placeholder | Value |
| --- | --- |
| `{player}` | quitting player name |
| `{uuid}` | quitting player UUID |
| `{attacker}` | retained incoming attacker display name, or empty |
| `{attacker_uuid}` | retained incoming attacker UUID, or empty |
| `{attacker_type}` | Bukkit entity type, or `UNKNOWN` |
| `{attacker_known}` | whether an incoming attacker is known |
| `{world}` | logout world |
| `{x}`, `{y}`, `{z}` | block coordinates |
| `{source_available}` | whether the original damage-source entity is still loaded |

Commands may be written with or without leading slashes. Leading slashes are removed during configuration loading; slash-only and blank commands are invalid.

## Commands and permissions

| Command | Permission | Purpose |
| --- | --- | --- |
| `/combattag status` | `serverfeatures.feature.combattag.command.status` | View your remaining time, opponent, and tag reason. |
| `/combattag status <player>` | `serverfeatures.feature.combattag.command.status.others` | Inspect another online player. |
| `/combattag untag <player>` | `serverfeatures.feature.combattag.command.untag` | Administratively clear a tag. |

Personal status is available to players by default. Other-player inspection and administrative untagging default to operators. The command root is required infrastructure; CombatTag refuses to complete startup if it cannot register `/combattag`.

## Public API

Consumers should depend on `serverfeatures-api` and use:

```java
CombatTagApi combatTags = CombatTags.service();

boolean tagged = combatTags.isTagged(player);
Optional<CombatTagSnapshot> snapshot = combatTags.getTag(player);
CombatTagResult result = combatTags.tag(player, opponent, CombatTagReason.EXTERNAL);
boolean removed = combatTags.untag(player, CombatUntagReason.EXTERNAL);
```

`CombatTags.service()` always returns a non-null service. It becomes a strict no-op when CombatTag is unavailable. Reads are safe from any thread. Write methods enforce server-thread usage and throw `IllegalStateException` when called asynchronously instead of allowing unsafe Bukkit access.

A snapshot exposes the player, current displayed opponent, the interaction reason represented by that opponent, tag and expiry instants, and remaining duration.

## Configuration

```yaml
enabled: true

tagging:
  mode: BOTH
  duration-seconds: 15
  allow-self-combat: false
  worlds:
    mode: ALL
    values: []

attribution:
  link-tamed-pets: true
  projectiles:
    enabled: true
    ignored-types:
      - EGG
      - ENDER_PEARL
      - SNOWBALL
  link-fishing-hooks: true
  link-primed-tnt: true
  mob-spawn-exclusions:
    - SPAWNER

lifecycle:
  clear-on-player-death: true
  clear-when-opponent-dies: true

teleport:
  prevent-portals: true
  prevent-other-teleports: true
  allowed-causes:
    - PLUGIN
    - UNKNOWN
    - ENDER_PEARL
  ender-pearl-resets-timer: false
  clear-after-allowed-teleport: false

logout-punishment:
  enabled: true
  kill-player: true
  broadcast: true
  punish-kicked-players: false
  commands: []

display:
  chat:
    enter: true
    exit: true
  action-bar:
    enabled: true
    update-interval-ticks: 5
    segments: 20
    filled-symbol: "█"
    empty-symbol: "█"

feedback:
  restriction-message-cooldown-millis: 1000
```

`tagging.worlds.mode` accepts `ALL`, `BLACKLIST`, or `WHITELIST`. World names are matched case-insensitively.

Configuration enum values are validated strictly. Unknown entity types, teleport causes, spawn reasons, modes, blank or slash-only command entries, no-op enabled punishment blocks, and unsafe numeric ranges prevent the feature from starting instead of silently weakening protection.

## Operational verification

1. Verify melee and projectile PvP reset both players' timers.
2. Verify mob attacks and player attacks against mobs follow the selected mode.
3. Test wolf damage with pet linking enabled and disabled.
4. Test the three ignored projectile defaults and verify disabling projectiles also disables fireworks.
5. Test fishing hooks and player-, mob-, and dispenser-created TNT.
6. Test an end crystal or another indirect player-caused explosion and verify the responsible player is resolved.
7. Confirm ignored projectiles and disabled TNT linking cannot be bypassed by generic damage-source attribution.
8. Confirm spawner mobs do not tag players before or after a feature reload or chunk reload.
9. Confirm cancelled and zero-final-damage events do not create or refresh tags.
10. Test every allowed teleport cause and both portal types.
11. Receive damage from one opponent, attack a second opponent, kill either one, and verify the remaining combat state and logout attribution stay correct.
12. Receive damage, attack another entity, then quit and verify the incoming attacker retains kill attribution.
13. Attack without first receiving damage, then quit and verify punishment occurs without false kill credit.
14. Kick a tagged player with `punish-kicked-players` disabled and enabled.
15. Stop or restart the server with tagged players online and confirm no logout punishment runs.
16. Replace the CombatTag action bar with another targeted override, then let combat expire and confirm CombatTag does not clear the newer override.
17. Test status, other-player status, administrative untagging, and all associated permissions.
18. Reload CombatTag and confirm active timers preserve only their remaining time.
19. Reload or disable CombatTag and confirm no listener, task, action bar, command, or API service remains.
