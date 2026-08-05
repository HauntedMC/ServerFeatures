# CombatTag

CombatTag is the native ServerFeatures combat-state authority and has no external combat plugin dependency.

## Behavior

A player is tagged whenever a configured combat interaction succeeds. Cancelled damage is ignored.

- `PVP` tags player-versus-player interactions.
- `MOBS` tags player-versus-mob interactions in both directions.
- `BOTH` enables both policies.
- dealing or receiving another qualifying hit replaces the displayed opponent and resets the full timer;
- incoming hits also update the attacker retained for logout punishment;
- outgoing hits never overwrite a still-active incoming-attacker attribution;
- a new tag sends the configured chat message;
- expiry or another configured untag cause sends the configured exit message;
- the remaining time is rendered through one bounded action-bar update task;
- tags are retained across a feature reload with only their actual remaining duration;
- tags are never persisted across a server restart.

The fixed bypass permission is:

```text
serverfeatures.feature.combattag.bypass
```

It defaults to operators. A bypassed player is not tagged, restricted, or punished. The bypass does not prevent that player from being recorded as the opponent of a non-bypassed player they attack.

## Attribution

CombatTag resolves the responsible attacker rather than treating every damage carrier as an unrelated entity.

Supported attribution includes:

- direct players and mobs;
- projectiles and their shooter;
- tamed pets and their online owner;
- primed TNT and its source;
- fishing hooks;
- area-effect clouds;
- evoker fangs;
- fireworks.

Pet linking applies only to the attacking side. Attacking somebody else's pet does not tag the pet owner.

When projectile linking is disabled, every projectile, including fireworks, is ignored. When it is enabled, configured entity types are still ignored. The default ignored projectile types are `EGG`, `ENDER_PEARL`, and `SNOWBALL`.

Mobs created through configured spawn reasons do not tag players. The default exclusion is `SPAWNER`. CombatTag reads Paper's persistent entity spawn reason at damage time, so the rule remains correct across feature reloads, chunk unloads, and existing loaded entities without maintaining a separate UUID cache.

## Death and teleport lifecycle

By default:

- a player is untagged when they die;
- every player tagged against an entity is untagged when that opponent dies;
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

The most recent incoming attacker is passed to Bukkit's damage API before the fallback kill so ordinary player or entity kill attribution is preserved when possible. If the player has only dealt damage during the current tag, the first outgoing opponent is used as the fallback attribution. The fallback kill guarantees the configured punishment even if another protection listener cancels the attributed damage.

Command placeholders:

| Placeholder | Value |
| --- | --- |
| `{player}` | quitting player name |
| `{uuid}` | quitting player UUID |
| `{attacker}` | retained logout attacker display name |
| `{attacker_uuid}` | retained logout attacker UUID |
| `{attacker_type}` | Bukkit entity type |
| `{world}` | logout world |
| `{x}`, `{y}`, `{z}` | block coordinates |
| `{source_available}` | whether the original damage source still exists |

Commands may be written with or without a leading slash.

## Public API

Consumers should depend on `serverfeatures-api` and use:

```java
CombatTagApi combatTags = CombatTags.service();

boolean tagged = combatTags.isTagged(player);
Optional<CombatTagSnapshot> snapshot = combatTags.getTag(player);
CombatTagResult result = combatTags.tag(player, opponent, CombatTagReason.EXTERNAL);
boolean removed = combatTags.untag(player, CombatUntagReason.EXTERNAL);
```

`CombatTags.service()` always returns a non-null service. It becomes a strict no-op when CombatTag is unavailable. Reads are safe from any thread; write methods must be invoked from the server thread.

A snapshot exposes the player, current opponent, latest tag reason, tag and expiry instants, and remaining duration.

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

Configuration enum values are validated strictly. Unknown entity types, teleport causes, spawn reasons, modes, blank command entries, and unsafe numeric ranges prevent the feature from starting instead of silently weakening protection.

## Operational verification

1. Verify melee and projectile PvP reset both players' timers.
2. Verify mob attacks and player attacks against mobs follow the selected mode.
3. Test wolf damage with pet linking enabled and disabled.
4. Test the three ignored projectile defaults and verify disabling projectiles also disables fireworks.
5. Test fishing hooks and player-, mob-, and dispenser-created TNT.
6. Confirm spawner mobs do not tag players before or after a feature reload or chunk reload.
7. Confirm cancelled damage does not create or refresh tags.
8. Test every allowed teleport cause and both portal types.
9. Receive damage, attack another entity, then quit and verify the incoming attacker retains kill attribution.
10. Quit against an online player, a mob, and an unloaded opponent.
11. Reload CombatTag and confirm active timers preserve only their remaining time.
12. Reload or disable CombatTag and confirm no listener, task, action bar, or API service remains.
