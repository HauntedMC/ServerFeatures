# LiquidTank

> Paper · Feature name `LiquidTank` · feature package `features.liquidtank` · disabled by default

LiquidTank turns specially signed hopper items into persistent multi-resource tanks. A placed tank remains a real hopper block, while its glass shell and liquid level are rendered to nearby clients with packet-only armor stands. Tank type and quantity are saved in `local/liquidtanks.yml`; loaded worlds use live `AbstractTank` instances and unloaded worlds use lightweight `UnloadedTank` records.

The implementation supports water, lava, milk, mushroom stew, rabbit stew, beetroot soup, honey, dragon breath, experience and empty state. It also supports player container interaction, automated XP deposit/withdrawal, tank-to-tank hopper transfer, chunk placement limits, world unload/load conversion, and a signed-item give command.

This is an older, complex feature with several important implementation caveats: some event work occurs asynchronously despite using Bukkit objects, an undocumented `enable-permission` config key is read, the chunk-limit key ignores world and mishandles negative coordinates, the visual packet IDs are reused per handler rather than per viewer, and several quantity/container edge cases are not transactionally guarded.

## Commands and permissions

Command root: `/liquidtank`, no aliases.

| Syntax | Permission | Sender | Behaviour |
|---|---|---|---|
| `/liquidtank give <player> [amount]` | `serverfeatures.feature.liquidtank.command.give` | Player only | Gives the exact online player a signed Liquid Tank hopper item stack. Default amount is 1. |

Console receives `general.player_command`; it cannot use the give command.

Amount must parse as integer >=1. There is no explicit maximum. Bukkit item-stack construction/Inventory rules govern oversized amounts.

The command calls `target.getInventory().addItem(...)` and ignores the returned overflow map. When the target inventory cannot accept the full stack, remainder items are not dropped or reported and can be lost.

Tab completion:

- first argument: `give`;
- second: exact local online player names;
- third: `1`, `2`, `3`, `5`, `10`, `16`, `32`, `64`.

### Player-use permission

```text
serverfeatures.feature.liquidtank.use
```

Required to place a signed tank. Tank interaction attempts check it only when an additional config flag `enable-permission` evaluates true. That flag is read but is **not generated in the default config**; see the configuration caveat below.

### Placement-limit bypass

```text
serverfeatures.feature.liquidtank.limit.bypass
```

Skips `amount-per-chunk` enforcement for signed-tank placement. It does not bypass the base use permission.

## Complete feature configuration

File: `plugins/ServerFeatures/features/LiquidTank/config.yml`.

| Key | Default | Exact behaviour and edge cases |
|---|---:|---|
| `enabled` | `false` | Loads persistent tank data, starts two repeating loops, registers four listeners and `/liquidtank`. |
| `item-name` | `&bLiquid Tank` | Display name for newly created signed hopper items. Parsed as mixed input with **colours only**. Cached during manager initialization. |
| `enable-items` | `true` | Controls decorative/functional physical hopper-inventory contents and whether tank hopper inventories are cleared during transfer guards. Cached at startup. |
| `amount-per-chunk` | `16` | Maximum tracked tanks per calculated chunk key unless bypass permission. Direct cast to `int`, no clamp. Cached at startup. |
| `enable-permission` | not generated | Read during async interaction permission checking through direct boolean cast. Missing/incompatible values throw inside a broad catch, which can silently prevent interaction. Add `enable-permission: true` or `false` explicitly until code/defaults are aligned. |

There are no config keys for tank capacities, transfer units, supported types, view range, packet scale, cooldown, XP rate, autosave interval, world allowlist, chunk key scope, placement material, item PDC version, particle/sound settings or command target mode. Those values are hard-coded.

### `amount-per-chunk` key bug

Placement uses:

```text
chunkKey = (blockX / 16) + "," + (blockZ / 16)
```

and compares that string across every loaded tank.

Consequences:

- world name/UUID is omitted, so identical chunk coordinates in different loaded worlds count against the same limit;
- Java integer division truncates toward zero, so negative coordinates are grouped incorrectly compared with Minecraft floor-based chunk coordinates. For example block X `-1` becomes chunk `0` rather than `-1`;
- unloaded-world records are not counted because only loaded tank list is scanned;
- count is O(total loaded tanks) for every placement;
- zero/negative maximum blocks all non-bypassed placements because `count < max` is false.

## Signed tank item contract

A legitimate item is a hopper with:

- material `HOPPER`;
- configured display name;
- PDC string key `<plugin namespace>:lt_kind` = `liquid_tank`;
- PDC integer key `<plugin namespace>:lt_ver` >=1.

Validation checks material, metadata, exact kind and version >=1. Display name and amount are not validated. Renaming or cloning the item preserves validity while PDC survives.

The item name parser enables colours only; decorations/gradients/click/hover are not enabled by `ItemCreator`.

## Persistence file

Path:

```text
plugins/ServerFeatures/local/liquidtanks.yml
```

Shape:

```yaml
tanks:
  10_64_-20_world:
    tankType: water
    quantity: 42
```

### Key format

```text
<x>_<y>_<z>_<worldName>
```

- first three segments parse as integers;
- all remaining segments are rejoined with `_`, so world names may contain underscores;
- malformed keys are warned/skipped;
- world name lookup uses `Server#getWorld(name)`.

### Value fields

| Field | Default | Behaviour |
|---|---:|---|
| `tankType` | null → empty | Normalized by removing underscores/lowercasing. Unknown/null values become `EMPTY`. |
| `quantity` | `0` | Loaded directly as integer; no capacity or non-negative validation. |

### Save representation

Tank type is saved as lowercase enum name with underscores removed:

- `mushroomstew`
- `rabbitstew`
- `beetrootsoup`
- `dragonbreath`
- etc.

Loaded live tanks and unloaded records are merged into one map. Duplicate coordinates/keys overwrite earlier entries in the output map.

### Save timing

- creating a new placed empty tank calls `quickSave(true,false)`, scheduling an async full-file rewrite and returning immediately;
- normal fill/drain/type-change/transfer operations do **not** immediately save;
- feature disable calls synchronous `save()`, which writes all tanks and then calls `clear()` on loaded tanks;
- world unload moves live state to unloaded records but does not explicitly save immediately;
- no repeating autosave task exists.

A crash after quantity changes but before feature/server disable can lose recent state. The asynchronous placement save iterates plain `ArrayList` state while main-thread mutations can occur, creating concurrency risks and inconsistent snapshots.

`clearAfter=true` hides packet visuals but does not remove hopper blocks.

## Tank types and capacities

| Type | Maximum quantity | Fill container/unit | Drain container/unit | Notes |
|---|---:|---|---|---|
| `EMPTY` | 128 base | Accepts all supported source containers | n/a | Converts to matching concrete type. |
| `WATER` | 128 | Water bucket +3; water potion +1 | Bucket -3; glass bottle -1 | Water bottle detection uses `PotionUtils`. |
| `LAVA` | 128 | Lava bucket +3 | Bucket -3 | |
| `MILK` | 128 | Milk bucket +3 | Bucket -3 | |
| `MUSHROOM_STEW` | 128 | Mushroom stew +1 | Bowl -1 | |
| `RABBIT_STEW` | 128 | Rabbit stew +1 | Bowl -1 | |
| `BEETROOT_SOUP` | 128 | Beetroot soup +1 | Bowl -1 | |
| `HONEY` | 128 | Honey bottle +1 | Glass bottle -1 | |
| `DRAGON_BREATH` | 128 | Dragon breath +1 | Glass bottle -1 | |
| `EXPERIENCE` | 1395 XP | XP bottle nominally +7; automatic deposit up to100/s | Glass bottle nominally -7; automatic withdrawal up to100/s | Several edge cases below. |

All non-XP display heights use hard-coded visual maximum 128. Experience overrides its liquid-level calculation for 1395.

## Player container exchange

`changeItemFromPlayer` handles source consumption/output:

- creative players receive/consume no item changes;
- stack size >1: decrement main-hand stack by one and add output item to inventory;
- stack size ==1: replace main-hand item with output;
- overflow from `addItem` is scheduled to drop at player's location.

The overflow loop iterates returned remainder values but schedules dropping the original `paramItemStack` for each remainder rather than the individual remainder value. With normal one-item outputs this often coincides, but code does not accurately preserve arbitrary remainder amounts.

There is no transaction rollback if inventory mutation succeeds and later tank-state/visual update fails.

### Exact-empty conversion

Most concrete types convert to `EmptyTank` only when quantity exactly equals one unit before drain. Quantities that are positive but below the required unit can become stuck for bucket types, and invalid persisted quantities can expose unusual behaviour.

## Experience-specific behaviour and edge cases

### Automatic loop

Every 20 ticks, for every Survival/Adventure player:

#### Withdraw

- inspect block exactly three blocks above current player block position;
- when it is an ExperienceTank hopper with quantity >0, grant up to100 XP and reduce tank;
- no sneaking required;
- empty tank converts to `EMPTY`;
- particles/visuals update.

#### Deposit

- require player sneaking;
- inspect block directly below current location;
- when tank is ExperienceTank, remove up to100 XP constrained by remaining capacity;
- when tank is EmptyTank, remove up to100 and convert to ExperienceTank;
- creative/spectator players are excluded.

This is automatic proximity transfer and has no permission check.

### XP bottle fill bug

ExperienceTank checks:

```text
quantity + 1 <= max
```

but adds **7**. At quantities 1389–1394, the check can pass and create quantity above 1395 by up to6.

### XP bottle drain semantics

With a glass bottle:

- quantity `<14`: gives one XP bottle and empties tank;
- quantity `>7`: otherwise subtracts7;

Thus quantities 0–6 can potentially produce an XP bottle if such invalid state is interacted with; quantities 8–13 consume all remaining XP for one bottle; quantity 7 also follows `<14` and gives one bottle. This does not require at least seven XP beyond persisted-state assumptions.

### Breaking XP tank

Non-creative break spawns one `ExperienceOrb` whose experience equals full tank quantity, then drops the signed tank item. There is no orb splitting or cap validation.

## Runtime visuals

Each tank owns packet handlers for:

- glass shell: glass item on an invisible client-side armor stand;
- liquid head: Base64-textured player head on a second packet armor stand.

`PacketHandler#show` generates a random positive entity ID, stores it in the handler, and sends spawn/equipment packets to the player. Because the handler has one `entityID` field and `show` is called separately per viewer, each new viewer overwrites the ID used later by `hide` for all viewers. Hiding an earlier viewer may send the most recently generated viewer's entity ID and leave client-side ghosts.

Tank visuals are not Bukkit entities and are not persisted/server-tracked. The feature uses raw packet classes and target-version compatibility must be maintained.

### View range

Per tank, a list of nearby **player names** tracks visibility.

- same world and distance <=20 blocks: show both packet entities and add name;
- when already tracked and distance >20: hide and remove name;
- exact-name tracking can be stale across name changes/reconnects;
- no UUID is used;
- no periodic view scan exists independent of movement/teleport/join/visual updates.

Every `PlayerMoveEvent` loops over every loaded tank and checks that one player, creating O(players moving × loaded tanks) work.

`updateVisuals()` hides existing packets, constructs new handlers/head height and then scans all online players.

## Tank action bar and cooldown

`playTitle` actually sends an action-bar progress string through `MessageUtils`, built from legacy colour codes, quantity and a calculated bar width. It is not a title packet.

Tank-to-tank transfer uses per-tank boolean cooldown for 50 ticks. `setOnCooldown()` schedules delayed reset. Repeated calls create multiple reset tasks; an earlier task can clear a newer cooldown early because no generation timestamp is tracked.

Normal player fill/drain does not check `onCooldown`.

## Placement event

`BlockPlaceEvent`, priority `HIGH`, manually returns when cancelled.

Requirements:

1. placed block is hopper;
2. item in hand passes signed PDC validation;
3. player has use permission;
4. bypass or `canPlaceTank` succeeds.

Success:

- create EmptyTank/runtime visuals;
- add to data list;
- schedule async full save;
- when `enable-items=true`, after two ticks put 7 glass in hopper slot3 and one comparator in slot4.

Failure generally cancels. Permission/chunk failure messages differ: missing use permission is silent; chunk limit sends hard-coded English action-bar text.

Any exception is swallowed after cancelling, with no log.

The physical hopper's decorative items are not PDC-marked/validated and can be altered by direct inventory API paths despite open/move protections.

## Interaction event and async safety

`PlayerInteractEvent`, `MONITOR`, `ignoreCancelled=true`.

The initial condition has operator-precedence behaviour:

```text
SURVIVAL || ADVENTURE || CREATIVE || (RIGHT_CLICK_BLOCK && clicked HOPPER)
```

For the first three game modes, **every interaction action/block** passes into the handler. It then schedules an asynchronous task that dereferences `getClickedBlock`, calls tank-manager/Bukkit location methods, cancels the event and checks permissions off-thread. Exceptions are swallowed.

Additional behaviour:

- sneaking with empty main hand cancels the event before confirming the clicked block is a tank;
- async callback looks up tank at clicked location;
- when found, it cancels the already-returned event off-thread;
- reads undocumented `enable-permission` boolean;
- schedules actual `tank.onInteract(player)` back on a one-time server task.

Cancelling an event asynchronously after listener return is not a reliable Bukkit pattern; vanilla hopper interaction may already proceed. This path should be redesigned to identify/cancel synchronously and only offload pure non-Bukkit work (none is required here).

## Hopper inventory and automation events

### Inventory open

`InventoryOpenEvent`, `LOWEST`, `ignoreCancelled=true`:

- only block hopper inventories with non-null location;
- when location is a tank, cancel open.

### Hopper pickup

`InventoryPickupItemEvent`, `LOWEST`, `ignoreCancelled=true`:

- tank hopper cannot pick up world items.

### Inventory move

`InventoryMoveItemEvent`, `LOWEST`, `ignoreCancelled=true`.

#### Source is tank hopper

When source tank is unpowered:

- clear source physical inventory when `enable-items=false`;
- cancel normal item transfer;
- when destination is also unpowered tank hopper, perform logical resource transfer.

When source tank is powered, this source branch does not cancel, so ordinary hopper inventory movement can occur unless destination guard catches it.

#### Destination is tank hopper

Always cancels normal move regardless of redstone power. Clears destination inventory when `enable-items=false`.

### Logical tank-to-tank transfer

Requirements:

- both tanks unpowered;
- neither tank on cooldown;
- source type not `EMPTY`.

Same type:

- fill destination to capacity from source;
- exact/less-than destination space empties source;
- source greater than space leaves remainder;
- visuals/cooldown updated.

Destination empty:

- if source `isOverFlown()` (`quantity > max`), destination becomes source type at source max, source reduces by max and receives cooldown;
- otherwise destination takes all source quantity and source becomes empty.

Different non-empty types do not transfer.

No immediate persistence save occurs. Event frequency is driven by hopper move attempts rather than an explicit transfer timer.

## Breaking and explosions

### Block break

`BlockBreakEvent`, `MONITOR`; manually returns when cancelled.

For a tracked hopper tank:

1. cancel event at `MONITOR` (contrary to monitor convention);
2. spawn XP orb for ExperienceTank in non-creative;
3. remove runtime tank/visuals;
4. clear hopper inventory;
5. drop one signed tank item in non-creative;
6. set block to air directly.

It does not save immediately. It also bypasses normal block-drop event flow by cancelling and directly dropping/setting air. Protection plugins cancelling before monitor are respected; same/later unusual listeners see direct mutation.

### Block explosion

`BlockExplodeEvent`, `HIGHEST`; when uncancelled, removes tracked tanks whose hopper blocks appear in `event.blockList()`.

It does not remove blocks from explosion list, cancel explosion or drop a signed item/resource contents. The physical hopper can be destroyed and tank state is removed. Entity explosions use `EntityExplodeEvent` and are not handled by this listener, so behaviour can differ depending on explosion source.

## World/player lifecycle

### World load

Default priority: convert matching `UnloadedTank` records into live tanks and visuals.

`loadUnloadedTankList` removes entries from the same `ArrayList` inside an enhanced for-loop, which can throw `ConcurrentModificationException`. Callers often wrap this in broad catches (teleport) but world-load path does not. Multiple entries for one world are therefore at risk of partial load/failure.

### World unload

For every live tank in the world:

- add unloaded record;
- collect live tank;
- remove live tank/visuals afterward.

No immediate save.

### Join

Schedules zero-tick delayed callback, loads unloaded tanks for player's world, and calls `updatePlayerView(player)` for all loaded tanks.

### Teleport

Default priority, no `ignoreCancelled`:

- tries to load destination world tank records;
- catches all exceptions silently;
- calls `updatePlayerView()` for every tank, which scans all online players.

Cancelled teleports can still trigger destination load/view updates.

### Move

Every move event loops all tanks and updates that player's visibility; no block-coordinate/world-change fast path.

## Integrity cleanup loop

Every 100 ticks:

- collect live tanks whose chunk/block is loaded and block type is no longer `HOPPER`;
- remove them from runtime/data and hide visuals.

Exceptions for the entire tick are swallowed. It does not save immediately or drop tank items/contents. It is a periodic reconciliation for external block changes.

## Messages and variables

| Key | Variables | Use |
|---|---|---|
| `liquidtank.given` | `{player}`, `{amount}` | Give-command acknowledgment. |
| `liquidtank.player_offline` | `{player}` | Exact target not online. |
| `liquidtank.invalid_amount` | none | Missing/non-positive numeric amount. |

Most gameplay feedback is hard-coded action-bar text/progress rather than localization. Missing placement permission and many failures are silent.

## Persistence, database and messaging

LiquidTank uses YAML only:

- no DataProvider/database;
- no Redis/proxy messages;
- no cross-server tank synchronization;
- no API registration;
- no PlaceholderAPI expansion.

World blocks and YAML are separate sources of truth. The five-second integrity loop removes YAML/runtime entries when loaded block is not hopper, but a hopper without YAML record remains ordinary.

## Lifecycle and shutdown

Initialization:

1. construct manager/data view;
2. load live/unloaded state;
3. start 100-tick integrity loop;
4. start 20-tick XP loop;
5. read feature config;
6. register block/interact/player/world listeners;
7. register command.

Config is read **after** data load and visual construction. Tank construction itself does not require these options, but early events are not dispatched until initialization completes.

Disable synchronously saves, then clears visuals through `clearAfter=true`. Lifecycle cleanup cancels loops/listeners/tasks. In-flight async quick-save/interaction tasks rely on shared lifecycle fencing; plain lists/config view are not designed for concurrent mutation.

## Developer source map

- Feature defaults/lifecycle: `features/liquidtank/LiquidTank.java`
- Manager/config/runtime orchestration: `features/liquidtank/internal/LiquidTankManager.java`
- YAML persistence: `features/liquidtank/config/LiquidTankDataHandler.java`
- Base and type implementations: `features/liquidtank/internal/tank/impl/`
- Type normalization: `features/liquidtank/internal/tank/TankType.java`
- Signed item: `features/liquidtank/internal/util/ItemCreator.java`
- Block events: `features/liquidtank/listener/TankBlockListener.java`
- Player/hopper interaction: `features/liquidtank/listener/TankInteractListener.java`
- Join/move/teleport: `features/liquidtank/listener/TankPlayerListener.java`
- World load/unload: `features/liquidtank/listener/TankWorldListener.java`
- Packet visuals: `features/liquidtank/internal/packet/`
- Give command: `features/liquidtank/command/LiquidTankCommand.java`

## Operational verification

1. Add explicit `enable-permission` and test true/false/missing states.
2. Test signed-item PDC validation, rename/clone and command inventory overflow.
3. Place at positive/negative chunk boundaries and identical coordinates across worlds.
4. Test all ten tank states and every fill/drain exact-boundary/invalid persisted quantity.
5. Test XP bottle quantities 0–14 and 1388–1395; verify documented over/underflow edge cases.
6. Test automatic XP above/below/sneaking/gamemode transfer.
7. Test same/different/empty tank-to-tank transfer, redstone power and 50-tick cooldown races.
8. Open/pickup/move physical hopper inventories with `enable-items` both ways.
9. Move many players near many tanks and profile O(players×tanks) visibility work.
10. Join/teleport/world load with multiple unloaded tanks and inspect list-removal failure.
11. Test block break, block explosion, entity explosion and external WorldEdit removal.
12. Crash/restart after quantity change versus graceful disable; verify persistence loss window.
13. Observe multiple viewers entering/leaving range for packet entity-ID ghosting.
14. Run Paper async-catcher/thread diagnostics during interactions and async saves.

## Troubleshooting

- **Right-click does nothing:** add valid `enable-permission`; interaction exceptions are swallowed, and cancellation occurs asynchronously.
- **Tank limit crosses worlds/negative chunks:** current key omits world and uses truncating division.
- **Tank state rolls back after crash:** only placement quick-save and graceful disable persist; no regular autosave.
- **Ghost/detached visuals:** packet handler stores one changing entity ID for multiple viewers; view tracking uses names.
- **Tank hopper GUI sometimes opens:** event is cancelled from an async callback after return; LOWEST InventoryOpen also protects known tank hoppers but race/integrations should be tested.
- **XP quantity exceeds capacity or bottle duplicates value:** ExperienceTank has documented +1-check/+7-add and `<14` drain semantics.
- **Tanks fail to load after world load:** `ArrayList` is modified during enhanced iteration.
- **Command says given but items missing:** inventory overflow result is ignored.
- **Explosion removed tank without item/content:** block explosion cleanup has no recovery drop; entity explosions are not covered.
- **Performance degrades:** every move scans every tank; visuals recreate/send packet entities frequently.
