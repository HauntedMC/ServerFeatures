# Portals

> Paper · Feature ID `portals` · disabled by default · local cuboid actions and proxy transfer

Portals stores named cuboid regions in `local/portals.yml`. When a player enters a matching block coordinate, the first matching definition performs one of three actions:

- teleport inside the same Paper process;
- execute a command as player or console;
- send a `BungeeCord` plugin message requesting a proxy server connection.

Optional delayed sound and particle feedback can follow the action. Editing uses one admin command and a PDC-marked Blaze Rod selection wand.

## Configuration surfaces

Feature config contains only:

```yaml
enabled: false
```

Every portal definition is persisted separately in:

```text
local/portals.yml
```

There are no feature-config settings for trigger cooldown, permission, movement polling, safety, messages, maximum size or destination validation. The trigger cooldown is hard-coded to one second.

## Portal YAML schema

```yaml
portals:
  example:
    mode: TELEPORT
    region:
      world: world
      x1: 0
      y1: 64
      z1: 0
      x2: 2
      y2: 66
      z2: 2
    teleport:
      world: world
      x: 100.5
      y: 70.0
      z: 100.5
      yaw: 0.0
      pitch: 0.0
    command:
      value: say {player} entered
      executor: CONSOLE
    server:
      name: survival
    exclusive_block: NETHER_PORTAL
    sound:
      name: minecraft:entity.enderman.teleport
      delay: 0
    particle:
      name: minecraft:portal
      delay: 0
```

### Fields

| Path | Required | Behavior |
|---|---|---|
| `mode` | no | `TELEPORT` default; valid enum values `TELEPORT`, `COMMAND`, `SERVER`. Invalid mode causes that portal to fail loading. |
| `region.world/x1..z2` | required to trigger | Inclusive integer cuboid. Coordinates are normalized min/max by `Region`. Missing region leaves a stored but inactive definition. |
| `teleport.world/x/y/z` | TELEPORT action | World name and exact doubles; yaw/pitch default zero. Missing/unloaded world makes runtime action fail. |
| `command.value` | COMMAND action | Command without leading slash. Blank means no command but effects can still run. |
| `command.executor` | COMMAND action | `PLAYER` or `CONSOLE`; invalid/missing falls back to console through `CommandExecutor.fromString`. |
| `server.name` | SERVER action | Proxy destination name. |
| `exclusive_block` | no | Trigger requires the player's destination block type to equal this Material. Invalid/non-block/air value is ignored on reload. |
| `sound.name` | no | Namespaced or compatibility-resolved Paper sound. Invalid value is ignored. |
| `sound.delay` | no | Delay ticks, clamped to zero or greater. |
| `particle.name` | no | Namespaced or compatibility-resolved particle. Invalid value is ignored. |
| `particle.delay` | no | Delay ticks, clamped to zero or greater. |

Portal IDs are stored under lower-case YAML keys and registry lookup is case-insensitive. A mixed-case ID can display with its original case during the current process, but after reload it is reconstructed from the lower-case key.

The registry preserves insertion/YAML order through a `LinkedHashMap`; that order determines which overlapping portal wins.

## Commands and permission

All operations use one permission:

```text
serverfeatures.feature.portals.admin
```

Command root: `/portals`. There are no granular permissions.

| Command | Sender | Behavior |
|---|---|---|
| `/portals create <id>` | any admin sender | Create default TELEPORT definition without region/destination. |
| `/portals delete <id>` | any | Remove definition/YAML subtree. |
| `/portals select <id>` | player | Store selected portal ID in editor state. |
| `/portals wand` | player | Give PDC-marked Blaze Rod; drops naturally when inventory is full. |
| `/portals saveregion` | player | Save current two wand points to selected portal. |
| `/portals setmode <id> <teleport|command|server>` | any | Change action mode. |
| `/portals setteleport <id>` | player | Save player's current exact location/yaw/pitch. |
| `/portals setcommand <id> <player|console> <command...>` | any | Save command; one leading slash is removed. |
| `/portals setserver <id> <serverName>` | any | Save one-token proxy server name. |
| `/portals setblock <id> <material|none>` | any | Set/clear exact required trigger block. |
| `/portals setsound <id> <key|none> [delayTicks]` | any | Set/clear sound. Invalid delay silently becomes zero. |
| `/portals setparticle <id> <key|none> [delayTicks]` | any | Set/clear particle. Invalid delay silently becomes zero. |
| `/portals info <id>` | any | Print stored mode, region, destinations, executor, block and effects. |

### Command inconsistencies

- Tab completion suggests `/portals list`, and `portals.list.*` messages exist, but `execute` has no `list` case. It silently does nothing.
- Unknown subcommands also silently do nothing.
- Deleting any nonblank unknown ID returns success because `deletePortal` removes the absent map/path and returns true.
- Invalid `setmode` is reported using the same not-found message as a missing portal.
- Invalid `setcommand` executor text defaults to console instead of rejecting it.
- Player-executed commands do not replace `{player}`; console commands do.
- `setblock` accepts any `Material#isBlock()` at command time; an air-like value can be stored but is rejected/ignored when reloaded because the registry additionally requires non-air.

Tab completion exposes IDs, modes, executors, placeable block enums and registry-backed sound/particle keys. ID filtering uses case-sensitive `startsWith` against a lower-cased input, so mixed-case current-process IDs may not suggest as expected.

## Wand format and selection state

The wand is:

- `BLAZE_ROD`;
- display name `§6Portals Wand` converted to an Adventure text component as literal text;
- PDC byte key `<serverfeatures namespace>:portals_wand`;
- hidden attributes.

`WandListener` checks PDC, not display name.

Main-hand left-click block stores pos1. Main-hand right-click block stores pos2. Both run at `HIGHEST`, `ignoreCancelled=true` and cancel the block interaction while the wand is held.

A portal must already be selected. The listener does not re-check admin permission, so a player who selected a portal before losing permission can continue using a retained wand/editor state. Wand items can also be transferred, though a recipient without selected state receives the “select first” message.

Selection maps are keyed by UUID and have no quit cleanup. They remain until feature object disposal/reload. Selecting another portal does not clear old pos1/pos2, so `/select new` followed immediately by `/saveregion` can copy the previous selection into the new definition.

Only same-world points are accepted. There is no WorldEdit integration; this feature uses its own wand.

## Region semantics

`Region#contains` compares:

- exact world name;
- `Location#getBlockX/Y/Z`;
- inclusive normalized min/max coordinates.

Sub-block position is irrelevant. Entering any point in a selected block counts as inside.

There is no size/volume limit, but runtime checking is constant-time per definition.

## Movement trigger

`PlayerMoveEvent` runs at `HIGHEST`, `ignoreCancelled=true`.

Events staying in the same block coordinate are ignored. On a block change, the handler scans all portals in registry order and chooses the first where:

1. region exists;
2. world name matches;
3. destination location is inside inclusive cuboid;
4. exclusive block, when configured, equals `to.getBlock().getType()`.

No trigger permission exists. Every player can activate every portal.

## Trigger cooldown and repeated entry

`lastTrigger: UUID -> epochMillis` enforces a fixed 1000 ms cooldown before scanning. The timestamp is written after a matching portal calls its handler, even when the action returns early due to missing teleport world/server target.

This is a throttle, not an enter/exit state. A player who remains in a large region and continues moving between blocks retriggers every second. There is no requirement to leave the region before another action.

The cooldown map is not cleared on quit and can retain UUIDs until feature reload/disable.

Overlapping regions are resolved by registry order and only one action runs per accepted movement.

## Vanilla portal override

`PlayerPortalEvent` runs at `HIGHEST`, `ignoreCancelled=true`.

When the event's `from` location is inside **any** configured portal region, the feature:

1. cancels vanilla portal travel;
2. calls `tryTrigger(player, from)`.

The precheck considers only region containment. It does not check:

- exclusive block;
- cooldown;
- mode destination validity.

Therefore vanilla Nether/End portal travel can be cancelled while no custom portal action occurs—for example because exclusive block mismatches, one-second cooldown is active, or destination is missing.

The override also uses `from`, while movement trigger uses `to`.

Other plugin teleports that place a player inside a region do not trigger immediately unless they also produce a qualifying block-change move or vanilla `PlayerPortalEvent`.

## TELEPORT mode

Runtime:

1. resolve target world by exact Bukkit world name;
2. when missing, log warning and return;
3. construct exact destination with yaw/pitch;
4. call synchronous `Player#teleport`;
5. log as teleported;
6. schedule effects.

The boolean teleport result is ignored. A protection plugin can cancel/return false while the feature still logs success and plays effects at the player's actual current location.

There is no safe-location, chunk-preload, vehicle/passenger or cross-world validation beyond world existence.

## COMMAND mode

When command is nonblank:

- `PLAYER`: `Player#performCommand(cmd)`; result logged; no `{player}` replacement.
- `CONSOLE`: replace every `{player}` with current player name and call `Bukkit.dispatchCommand`; result logged.

A blank/missing command performs no primary action but still schedules configured effects.

The feature does not escape names or command content and does not enforce a command allowlist. Portal administrators have effective command configuration authority.

## SERVER mode

When target is missing/blank, the player receives `portals.server.missing`, a warning is logged and no effects are scheduled.

Otherwise the handler writes this plugin message:

```text
channel: BungeeCord
subchannel: Connect
server: configured name
```

The outgoing channel is registered on feature initialize and unregistered on disable. Velocity must have compatible BungeeCord plugin-message handling enabled.

Send exceptions are logged inside `connectPlayerToServer`, but the caller still logs that the player was sent and schedules effects. There is no proxy acknowledgement, timeout, destination availability check or fallback.

The message is carrier-dependent: it is sent through the player's connection. A disconnect/routing race can lose it.

## Sound and particle ordering

Effects are scheduled **after** primary action invocation:

- sound at player's location after `sound.delay`;
- 30 particles around player's then-current location after `particle.delay`;
- callback requires `player.isOnline()`.

For a successful local teleport, effects normally occur at destination. For a proxy transfer, the player may already be gone and the callbacks do nothing. For failed teleport/plugin-message operations whose handler did not early-return, effects can still play on the original backend.

There is no session/generation check; a rapidly reconnecting player object/UUID lifecycle depends on Bukkit task semantics.

## Persistence lifecycle

`PortalRegistry` loads every child beneath `portals` independently. A per-definition exception logs and skips that portal while later definitions continue.

`savePortal` batches the current model into YAML and updates the in-memory map. Sound/particle/exclusive block are removed when explicitly cleared. There are no clear commands for teleport, command or server target.

Create writes a definition immediately. An incomplete portal remains persisted and can later be configured.

Delete removes every known subsection and the base node. As noted, any nonblank ID returns true even if it did not exist.

No automatic file watcher/reload command exists. Manual YAML edits require feature/server reload.

## Messages and unused keys

The feature defines full admin feedback for create/delete/select/positions/region/mode/destinations/block/effects/info.

Currently unused or inconsistent keys include:

- `portals.list.header` and `portals.list.entry`: no executable list branch;
- `portals.wand.must_hold` and `portals.wand.block_click_denied`: listener does not send them;
- some generic usage is hard-coded with `§c` rather than localization.

No PlaceholderAPI expansion is registered.

## Lifecycle and cleanup

Disable only unregisters outgoing `BungeeCord` channel. Lifecycle manager removes command/listeners/tasks.

The handler does not explicitly clear selection/cooldown maps, but the feature releases handler/registry references during teardown. Pending effect tasks rely on lifecycle task cancellation.

Portal YAML remains unchanged.

## Performance

Each qualifying block movement scans every portal until first match: worst-case `O(number of portals)` per moving player per block transition. No world/chunk spatial index exists.

Region checks are simple integer comparisons. Large portal counts—not region volume—drive runtime movement cost.

## Important implementation boundaries

- One admin permission controls all configuration.
- Runtime activation has no permission.
- One-second throttle is not enter/exit debouncing.
- First matching portal in YAML/registry order wins.
- Selection/cooldown maps lack quit cleanup.
- Vanilla portal event is cancelled before exclusive/cooldown/destination checks.
- TELEPORT ignores teleport result.
- SERVER has no acknowledgement and can log success after send failure.
- Player command mode does not replace `{player}`.
- Tab suggests unimplemented `list`.
- Delete reports success for unknown nonblank IDs.
- Mixed-case IDs normalize after reload.
- Manual YAML changes are not watched.
- No safe destination/chunk loading exists.

## Verification checklist

1. Exercise every command from player/console with and without admin permission.
2. Test `list`, unknown delete, invalid mode/executor/delay/material and mixed-case IDs.
3. Select one portal, switch selection without resetting points and save.
4. Transfer/reuse wand after permission removal and across players.
5. Create overlapping regions and verify first registry order.
6. Stand/move inside a large region for several seconds and confirm repeated triggers.
7. Test failed/missing TELEPORT, COMMAND and SERVER destinations and resulting cooldown/effects/logs.
8. Cancel `Player#teleport` through another plugin and compare actual position with log/effects.
9. Place a custom region around a vanilla portal with cooldown/exclusive mismatch and verify vanilla cancellation.
10. Test BungeeCord plugin messaging on production Velocity configuration and unavailable server names.
11. Quit/rejoin repeatedly and inspect selection/cooldown map behavior.
12. Load-test many portals with many moving players.
13. Reload/disable with pending delayed effects and active editor state.

## Source map

- Defaults/messages/channel lifecycle: `features/portals/Portals.java`
- Admin command/completion: `features/portals/command/PortalsCommand.java`
- Editor state, runtime actions, cooldown and effects: `features/portals/internal/PortalsHandler.java`
- YAML load/save: `features/portals/registry/PortalRegistry.java`
- Definition/action model: `features/portals/model/PortalDefinition.java`, `PortalMode.java`, `CommandExecutor.java`
- Inclusive cuboid: `features/portals/model/Region.java`
- Movement: `features/portals/listener/PortalsListener.java`
- Wand: `features/portals/listener/WandListener.java`
- Vanilla portal override: `features/portals/listener/PortalOverrideListener.java`
- Registry parsing: `features/portals/util/RegistryUtil.java`
