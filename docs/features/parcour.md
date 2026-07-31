# Parcour

> Paper · Feature name `Parcour` · feature package `features.parcour` · disabled by default

Parcour is a complete local parkour-session system. Administrators define a start cuboid, ordered checkpoint cuboids and an end cuboid from their WorldEdit selection, then configure locations, restoration behaviour, commands, particles, sounds, potion effects, countdowns, action-bar timing, capacity, cooldowns and a start kit. Players start a named course, receive a protected temporary session state, progress through checkpoints in strict order, can return to the last restore point, and have their original inventory/game state restored when leaving, failing, dying or completing.

Definitions are persisted in `local/parcours.yml`. Active player sessions, timers, cooldowns and snapshots are in memory only. There is no MySQL/DataRegistry/Redis/PAPI integration.

## Dependencies and startup

WorldEdit is required for region-authoring commands. The command uses WorldEdit's current `LocalSession` selection and adapts it to a Bukkit-world cuboid.

Initialization:

1. load `local/parcours.yml` into `ParcourRegistry`;
2. construct `ParcourHandler` and runtime state;
3. register the main movement/lifecycle listener;
4. register item/protection/death/void listeners;
5. register `/parcour`.

Invalid/missing WorldEdit selections are reported as `parcour.admin.region.missing`. Region commands are player-only because they need the invoking player's WorldEdit selection/location.

## Permissions

| Permission | Scope |
|---|---|
| `serverfeatures.feature.parcour.use` | Base access/help root. |
| `serverfeatures.feature.parcour.command.start` | Start a course. |
| `serverfeatures.feature.parcour.command.leave` | Leave an active course. |
| `serverfeatures.feature.parcour.command.checkpoint` | Return to last restore/checkpoint location. |
| `serverfeatures.feature.parcour.admin` | Complete administration tree, map creation/deletion, regions, settings, kit/effect commands and map information. |

The Brigadier root is visible/executable when the sender has either base or admin permission. Individual player subcommands require their specific node. Administrative commands require only the admin node.

## Player commands

| Syntax | Behaviour |
|---|---|
| `/parcour start <id>` | Begin the named course when it exists, is configured, has capacity, the player is not already active/inside a prohibited start state, and any start cooldown has expired. |
| `/parcour leave` | Abort the active session and restore the player's pre-parcour state/leave destination. |
| `/parcour checkpoint` | Teleport to the last configured restore point when one exists and checkpoint cooldown permits it. |
| `/parcour` | Shows player help; admins additionally receive categorized admin help. |

Player commands are player-only. Course IDs are suggested from the registry.

## Administrative command tree

All commands below require `serverfeatures.feature.parcour.admin`.

### Maps

| Command | Behaviour |
|---|---|
| `/parcour createmap <id>` | Create a new empty definition. |
| `/parcour deletemap <id>` | Delete definition/config. Active sessions using it must be considered operationally before deletion. |
| `/parcour maplist` | List every map with region count. |
| `/parcour mapinfo <id>` | Display regions and current settings. |

### Regions from WorldEdit selection

| Command | Behaviour |
|---|---|
| `/parcour addregion start <id> [restore]` | Set start cuboid; optional restore flag. |
| `/parcour addregion end <id>` | Set end cuboid; end regions do not use restore. |
| `/parcour addregion checkpoint <order> <id> [restore]` | Add/replace ordered checkpoint. Order accepts Brigadier range `0..10000`. |
| `/parcour deleteregion <id> <START|END|checkpointOrder>` | Remove region. |
| `/parcour setrestore <id> <START|checkpointOrder> <true|false>` | Toggle region restore behaviour; end is not applicable. |
| `/parcour setrestorelocation <id> <START|checkpointOrder> [clear]` | Set region-specific restore location from player's current position, or clear it. |

WorldEdit minimum/maximum block coordinates and world name are stored. Selection failure, incomplete selection, adaptation errors or unavailable WorldEdit all resolve to the missing-selection response.

### Region commands

| Command | Behaviour |
|---|---|
| `/parcour setcmd add <id> <START|END|checkpointOrder> <command...>` | Append command to region. |
| `/parcour setcmd remove <id> <key> <oneBasedIndex>` | Remove command by displayed one-based index. |
| `/parcour setcmd clear <id> <key>` | Clear commands for region. |
| `/parcour list <id> <key>` | List commands for region. |

Commands are persisted in order and executed when the corresponding region is accepted by session progression. Configuration should omit a leading slash unless handler normalization explicitly accepts it. Review every command for player/console execution semantics in the handler before using privileged actions.

### Course locations

| Command | Behaviour |
|---|---|
| `/parcour setleavelocation <id>` | Set leave destination to admin's current location. |
| `/parcour setleavelocation <id> clear` | Clear it. |
| `/parcour setfinishlocation <id>` | Set completion destination. |
| `/parcour setfinishlocation <id> clear` | Clear it. |
| `/parcour setstartposition <id>` | Set precise start position/orientation. |
| `/parcour setstartposition <id> clear` | Clear it. |

A configured world must be loaded when resolving a location. Missing worlds yield empty optional locations and fallback/restore behaviour follows the handler.

### General settings

| Command | Stored setting |
|---|---|
| `/parcour setprogressnotify <id> <boolean>` | Chat checkpoint-progress notifications. |
| `/parcour setactionbar <id> <boolean>` | Repeating elapsed/progress action bar. |
| `/parcour setfinishdelay <id> <0..3600>` | Seconds before finish teleport/state restoration. |
| `/parcour setfinishactionbarhold <id> <milliseconds>` | Completion action-bar hold duration. |
| `/parcour setmaxplayers <id> <count>` | Simultaneous active-session capacity. |
| `/parcour setstartcooldown <id> <seconds>` | Cooldown before starting again. |
| `/parcour setcheckpointcooldown <id> <seconds>` | Cooldown for checkpoint-return action. |
| `/parcour setstartcountdown <id> <seconds>` | Frozen/countdown phase before timer/progression. |
| `/parcour sethunger <id> <boolean>` | Whether hunger changes are allowed during course. |
| `/parcour setdamage <id> <boolean>` | Whether damage is allowed during course. |

### Particles and sounds

| Command | Behaviour |
|---|---|
| `/parcour setregionparticle <id> <particle|clear>` | Particle used to outline regions. |
| `/parcour setcheckpointparticle <id> <particle|clear>` | Checkpoint/progress particle. |
| `/parcour setparticleinterval <id> <ticks>` | Outline/particle cadence; model clamps to at least one tick. |
| `/parcour setparticlepoints <id> <points>` | Target outline sample count; clamps to at least one. |
| `/parcour setsound <id> <CHECKPOINT|END> <sound|clear>` | Bukkit registry sound key. Invalid sound is rejected. |

Particle/sound arguments are validated through Bukkit registries. Namespaced keys are preferred.

### Potion effect

```text
/parcour seteffect <id> clear
/parcour seteffect <id> <effect> [amplifier 0..255]
```

The effect key is validated against Bukkit's mob-effect registry. Model stores uppercase normalized type text and a non-negative amplifier. Effect ownership/removal is handled as part of session restore; test interaction with pre-existing effects.

### Start kit and utility items

The command tree includes start-kit management and item configuration:

- capture/add/remove/list/clear serialized start-kit items;
- configure material keys for leave/checkpoint utility items;
- configure slots (model clamps `0..35`);
- enable/disable each utility item.

Defaults in `ParcourDefinition`:

| Setting | Default |
|---|---|
| Leave item | `minecraft:barrier`, slot `5`, enabled |
| Checkpoint item | `minecraft:nether_star`, slot `3`, enabled |

Start-kit entries are serialized Base64 item data. Treat the YAML as opaque and use commands rather than hand-editing serialized values.

## `local/parcours.yml` data model

Root:

```yaml
parcours:
  example:
    # course settings
    start: ...
    end: ...
    checkpoints: ...
```

The registry persists each `ParcourDefinition` and its regions. Exact generated key spelling should be taken from the written file/registry methods; important model fields are:

### Course-wide fields

- progress notification;
- action-bar enable;
- finish teleport delay seconds;
- finish action-bar hold milliseconds (model default 3000);
- region highlight particle;
- particle interval ticks (default 12, minimum1);
- particle outline target points (default280, minimum1);
- hunger enabled (default true);
- damage enabled (default true);
- checkpoint cooldown seconds (minimum0);
- start countdown seconds (minimum0);
- start position;
- leave position;
- finish position;
- start-kit serialized item list;
- potion effect type/amplifier;
- checkpoint/end sound;
- leave/checkpoint item materials, slots and enabled flags;
- max players/start cooldown as loaded by registry.

### Region data

Every region stores:

- type (`START`, `CHECKPOINT`, `END`);
- checkpoint order where applicable;
- cuboid world and inclusive min/max coordinates;
- `restore` boolean for start/checkpoint;
- optional precise restore location;
- ordered command list.

Checkpoint storage is a `TreeMap<Integer, ParcourRegion>`, so logical order is numeric regardless of YAML child order.

## Session start and snapshot model

A player can have one active `PlayerParcourState`. Start validates definition/session/capacity/cooldown and creates a snapshot of relevant original state before applying the course environment.

The runtime state includes course identity, start time, next/last checkpoint information, restoration snapshot, last safe/restore location, countdown/finish phases and task references. Exact snapshots include inventory/armor/offhand and player gameplay state handled by `PlayerSnapshot`/handler.

Start may:

- teleport to configured start position/region restore point;
- clear/replace inventory with start kit and utility items;
- apply potion effect;
- freeze player during countdown;
- initialize elapsed timer/action bar;
- execute start-region commands;
- register active capacity/cooldown state.

A failed restoration emits `parcour.failed_restore`; operational testing must cover inventories, armor, offhand, XP, health/food, gamemode, flight and potion interactions represented by the snapshot implementation.

## Movement and checkpoint ordering

`PlayerMoveEvent` is the primary progression detector. For active sessions it evaluates the player's destination/location against current definition regions.

Expected progression:

1. start accepted/session begins;
2. checkpoints must be entered in ascending configured order;
3. entering an already-completed or future checkpoint can produce `parcour.out_of_order`/no progress;
4. accepted checkpoint updates state, optional restore location, action bar/chat, sound/particle and commands;
5. end is accepted only after required checkpoints;
6. completion starts finish handling/delay/teleport/restore.

The handler prevents repeated trigger spam by tracking current region/progress/cooldowns. Cuboids are block-coordinate regions and region containment semantics should be tested at inclusive boundaries.

Out-of-bounds/void/death paths abort or restore according to listener/handler rules and messages such as `parcour.out_of_bounds`, `parcour.death` and `parcour.failed_restore`.

## Action bar and timer

When enabled, the action bar uses `parcour.actionbar.progress` with `{elapsed}`, `{checkpoint}`, and `{total}`. Checkpoint cooldown uses `parcour.actionbar.cooldown` with `{seconds}`. Completion uses a finish action-bar hold period configured in milliseconds.

Timing is runtime-local and lost on restart/reload.

## Checkpoint return

`/parcour checkpoint` and the checkpoint utility item route to the restore action. A restore point exists only after an accepted restore-enabled start/checkpoint and a resolvable explicit/derived safe location. It respects checkpoint cooldown and does not advance progress.

## Utility-item and gameplay protections

`ParcourItemListener` recognizes the leave/checkpoint controls and prevents normal item behaviour while active. `ParcourProtectionListener` protects the temporary snapshot/session from inventory mutations, drop/pickup, hand swaps, block actions and configured hunger/damage restrictions. Death/void and quit/teleport lifecycle are handled by dedicated listeners.

Direct plugin API mutations can bypass Bukkit events; integrations must avoid changing session inventory/state or explicitly coordinate with `ParcourHandler`.

## Capacity, cooldowns and commands

Capacity counts active sessions for the definition. Start cooldown/checkpoint cooldown are in-memory and reset on restart/backend switch. Region commands execute only on accepted ordered progression and should avoid blocking operations, recursive session commands or permanent item changes overwritten by snapshot restoration.

## Messages and variables

Player variables:

| Key | Variables |
|---|---|
| `parcour.not_found` | `{name}` |
| `parcour.started` | `{name}` |
| `parcour.completed` | `{name}` |
| `parcour.checkpoint` | `{checkpoint}` |
| `parcour.full` | `{max}` |
| `parcour.cooldown` | `{seconds}` |
| `parcour.actionbar.progress` | `{elapsed}`, `{checkpoint}`, `{total}` |
| `parcour.actionbar.cooldown` | `{seconds}` |

Other player messages cover already active, left, no active, no checkpoint, invalid checkpoint, failed restore, timer, death, out-of-bounds, flying, out-of-order and cannot-start-inside. Admin messages expose IDs/types/orders/values/locations/indices as used by each command.

## Persistence and messaging summary

- Definitions: `local/parcours.yml`.
- Active sessions/snapshots/cooldowns: memory only.
- DataProvider/database: none.
- Redis/proxy messaging: none.
- PlaceholderAPI: none.
- Java registered API: none; handler/registry available through feature instance.
- Cross-backend active sessions: unsupported.

## Lifecycle and reload

Registry command mutations persist immediately. Feature reload reconstructs definitions. Editing/deleting maps during active sessions is unsafe unless all needed definition data is already captured. Feature disable must restore every active player and cancel countdown/action-bar/finish tasks before listener/task teardown.

## Developer source map

- Defaults/messages/lifecycle: `features/parcour/Parcour.java`
- YAML definitions: `features/parcour/registry/ParcourRegistry.java`
- Models/snapshots: `features/parcour/model/`
- Runtime state machine: `features/parcour/internal/ParcourHandler.java`
- Command tree/WorldEdit: `features/parcour/command/ParcourCommand.java`
- Main events: `features/parcour/listener/ParcourListener.java`
- Death/void: `features/parcour/listener/ParcourDeathVoidListener.java`
- Protection: `features/parcour/listener/ParcourProtectionListener.java`
- Utility items: `features/parcour/listener/ParcourItemListener.java`
- Tests: `src/test/.../features/parcour/`

## Operational verification

1. Create map and inspect generated YAML/defaults.
2. Define regions in every coordinate orientation/world and test inclusive edges.
3. Test missing WorldEdit selection/dependency and unloaded worlds.
4. Start with every inventory/armor/offhand/gamemode/health/food/XP/flight/effect state and verify restoration.
5. Test zero/multiple/non-contiguous checkpoints and out-of-order entry.
6. Test restore flags/locations and cooldown.
7. Test leave/item/quit/kick/death/void/respawn/world change/external teleport.
8. Test countdown freeze, action bar, finish delay/hold and task cancellation.
9. Test max players/start cooldown and simultaneous starts.
10. Test all protected inventory/gameplay routes.
11. Test configured commands/effects/sounds/particles and invalid keys.
12. Disable/reload during every phase and verify no loss/duplication/late callback.

## Troubleshooting

- **Region command says missing:** WorldEdit selection/world could not be obtained.
- **Checkpoint ignored:** entered out of numeric order or still considered inside prior region.
- **Checkpoint return unavailable:** last accepted region did not enable restore or world/location cannot resolve.
- **Inventory/state lost:** inspect snapshot/finalization path and external plugin mutations.
- **Player stuck frozen:** countdown/finish task was interrupted without state finalization.
- **Commands/items disappear on finish:** original snapshot restoration overwrites temporary course state.
- **Cooldown/capacity resets after restart:** runtime state is not persistent.
- **Other backend continues session expectation:** no proxy/Redis contract exists.
- **Particles cause load:** reduce cadence/target points after profiling.
