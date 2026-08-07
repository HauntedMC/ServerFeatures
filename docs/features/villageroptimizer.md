# VillagerOptimizer

> Paper · Feature ID `villageroptimizer` · disabled by default · persistent per-villager AI and restock state

VillagerOptimizer is interaction-driven. It does not scan villagers by distance or run a periodic optimizer. Players use an Emerald Block to switch a villager's awareness, disabled villagers restock when interacted with at configured day-time thresholds, and closing a trade can briefly re-enable awareness so vanilla can process a pending level-up.

State is stored on the villager through PersistentDataContainer and normally survives chunk unload and world saves.

## Commands, permissions and integrations

There is no command, PlaceholderAPI expansion, database, Redis contract or public feature service.

| Permission | Effect |
|---|---|
| `serverfeatures.feature.villageroptimizer.toggle.bypass` | Ignores the per-villager awareness-toggle cooldown. |
| `serverfeatures.feature.villageroptimizer.restock.bypass` | Resets all recipe uses on every eligible interaction and stores the new restock time. |
| `serverfeatures.feature.villageroptimizer.restock.notify` | Shows the time until the next configured restock. |

There is no general use permission. Authorization otherwise depends on the GriefPrevention compatibility check when that plugin is enabled.

## Configuration

| Key | Default | Meaning |
|---|---:|---|
| `enabled` | `false` | Registers the event listener and handlers. |
| `cooldown` | `600` | Seconds between awareness toggles for one villager. |
| `restockTimes` | `[1000, 13000]` | World day-time ticks used as restock thresholds. |

Values are captured during handler construction.

### Numeric type boundary

The source uses strict casts:

- the default cooldown is inserted as `600L`, while `VillagerAIHandler` reads `(long) (int) configValue`;
- default restock values are inserted as `Long`, while `VillagerRestockHandler` requests `List<Integer>`.

The config layer may normalize these values, but this must be verified. A framework change that preserves the original Java types can cause initialization failure or an empty effective restock list.

There are no world, profession, distance, age, update-interval or trade-limit settings.

## PersistentDataContainer fields

Keys use the ServerFeatures plugin namespace.

| Key | Type | Value |
|---|---|---|
| `cooldown` | `LONG` | Absolute Unix epoch seconds for the next allowed awareness toggle. |
| `time` | `LONG` | World `fullTime` of the last feature restock/initialization. |
| `levelCooldown` | `LONG` | Absolute Unix epoch seconds for the temporary level-processing window. |
| `disabledByBlock` | `STRING` | Literal `true` or `false`. |

Fields are not removed when false or expired. `hasDisabledByBlock` checks key presence; `getDisabledByBlock` parses its value. A villager toggled back on therefore retains the key with value false.

No schema marker or conversion process exists.

## Event ordering

Every listener method uses bare `@EventHandler`:

- priority is `NORMAL`;
- cancelled events are still processed.

| Event | Purpose |
|---|---|
| `PlayerInteractEntityEvent` | Initialize/repair PDC, enforce level cooldown, toggle awareness and evaluate restock. |
| `InventoryClickEvent` | Prevent trade interaction unless marker value is true. |
| `TradeSelectEvent` | Prevent recipe selection unless marker value is true. |
| `InventoryCloseEvent` | Open a temporary awareness window for pending level-up. |
| `EntityDamageByEntityEvent` | Protect marker-true villagers from direct Zombie damage. |

The feature can therefore close a merchant screen or modify a villager even when another plugin already cancelled the event.

## GriefPrevention check

For villager right-clicks, the listener checks at runtime whether GriefPrevention is enabled. The compatibility method:

1. queries the claim at the **player's location**;
2. calls deprecated `claim.allowBreak(player, Material.SPAWNER)`;
3. allows when no claim exists or the denial result is null.

It does not query the villager's location and does not display a feature message when denied. SPAWNER break access is used as the authorization proxy.

When GriefPrevention is absent, the interaction proceeds.

## Right-click initialization and sanity repair

On an allowed villager interaction, missing fields are initialized:

- toggle cooldown to the current epoch second;
- level cooldown to the current epoch second;
- stored restock time to current world `fullTime`.

`sanityChecks` then repairs unusual future/time-reset values:

- level cooldown beyond now + 10 seconds becomes now + 5;
- toggle cooldown beyond now + twice the configured cooldown becomes now + one cooldown;
- stored full time greater than current world full time becomes current full time.

These checks run only when a player interacts.

If marker value is true and level cooldown is still active, the player receives `cooldownLevelupMessage`, the villager shakes its head, the event is cancelled and later toggle/restock logic is skipped.

## Emerald Block awareness toggle

When the event is not cancelled and the main-hand item is `EMERALD_BLOCK`, the feature toggles `Villager#isAware()` and cancels the interaction. The block is not consumed.

### Aware to unaware

1. enforce cooldown unless bypass permission;
2. set `aware=false`;
3. store `disabledByBlock=true`;
4. store cooldown now + configured seconds;
5. send `AIdisabled`.

### Unaware to aware

1. enforce the same cooldown;
2. set `aware=true`;
3. store `disabledByBlock=false`;
4. store a new cooldown;
5. send `AIenabled`.

The implementation does not preserve why a villager was previously unaware or which system owned that state.

After toggle processing, marker-true villagers run restock evaluation. When such a villager reports `hasAI=false`, the feature sets AI true and awareness false.

## Trade gating

For merchant inventories held by `Villager`, both inventory clicks and trade selection are allowed only when `getDisabledByBlock(villager)` is true.

Otherwise the feature:

1. cancels the event;
2. closes the inventory;
3. sends `villagerMustBeDisabled`.

Untouched villagers and false-marker villagers therefore cannot be traded through these event paths. The feature is not merely reducing background cost; its marker becomes a trading requirement.

The listener assumes the clicking entity is a Player.

## Restock behavior

There is no scheduled restock task. Evaluation happens only when a player right-clicks a marker-true villager.

### Immediate cases

- restock bypass permission: reset all recipe uses and store current full time;
- missing `time` key: same immediate restock/initialization.

### Threshold cases

For each configured time:

```text
currentDayStart = world.fullTime - world.time
todayThreshold = currentDayStart + configuredTick
```

Restock occurs when current full time has reached the threshold and the villager's stored time is earlier than it. The first matching threshold wins.

`restock` loops through `Villager#getRecipes()` and calls `MerchantRecipe#setUses(0)`. It does not explicitly call `setRecipes` afterward.

When no restock occurs and the player has notification permission, the nearest future threshold is rendered. If all thresholds passed, the first list value is used for the next day. An empty effective list therefore causes `getFirst()` to fail in this path.

Duplicate, unsorted and out-of-day values are not validated.

## Level-up window

On merchant inventory close:

- Wandering Traders are ignored;
- inventory type must be `MERCHANT`;
- holder must be non-null and is cast to `Villager`;
- the feature checks **presence** of `disabledByBlock`, not its boolean value.

Thus a villager toggled back on still enters level processing after trade close.

Expected level from experience:

| XP | Level |
|---:|---:|
| `0–9` | 1 |
| `10–69` | 2 |
| `70–149` | 3 |
| `150–249` | 4 |
| `250+` | 5 |

When actual level is lower and no level cooldown is active, the feature:

1. stores level cooldown now + 5 seconds;
2. adds `SLOW_FALLING` for 120 ticks with amplifier 120 and no particles;
3. sets awareness true;
4. schedules awareness false after 100 ticks.

It does not set the villager level directly; vanilla is expected to process it while aware.

The delayed callback does not revalidate entity validity, marker value, later player changes or feature generation. A later manual/plugin awareness change can therefore be overwritten.

## Zombie protection

Direct Zombie damage is cancelled only when the victim is a Villager and marker value parses true. Other mobs, indirect projectiles, environmental damage and player damage are outside this handler.

## Messages

| Key | Variables |
|---|---|
| `villageroptimizer.AIdisabled` | none |
| `villageroptimizer.AIenabled` | none |
| `villageroptimizer.cooldownBlockMessage` | `{time_min}`, `{time_sec}` |
| `villageroptimizer.cooldownLevelupMessage` | `{time_sec}` |
| `villageroptimizer.nextRestock` | `{time_min}`, `{time_sec}` |
| `villageroptimizer.villagerMustBeDisabled` | none |

No message is sent for GriefPrevention denial or successful automatic restock.

## Lifecycle and cleanup

`disable()` is empty. The feature does not:

- restore awareness;
- restore prior AI state;
- remove PDC fields;
- scan loaded/unloaded villagers;
- normalize pending level tasks itself.

A villager left unaware can remain unaware after feature disable, while the trade-gating listener is no longer present. State also follows entity copies/moves when the tool preserves PDC.

## Performance

There is no idle scan cost. Work occurs on interactions/trade close, with recipe iteration during restock. This keeps background overhead low but means restock and state repair depend on players touching the villager.

## Important boundaries

- Awareness, not distance-based AI scheduling, is the central mechanism.
- Trading requires marker value true.
- PDC state survives feature disable/restart.
- Prior state ownership is not retained.
- Restocks are interaction-driven.
- GriefPrevention checks player location and SPAWNER access.
- All handlers process cancelled events at NORMAL.
- Level close checks marker presence, not value.
- Delayed level callback can overwrite newer awareness state.
- Direct Zombie damage is the only protected damage path.
- Config numeric types require verification.

## Verification checklist

1. Inspect generated cooldown/restock runtime types and initialize with untouched defaults.
2. Toggle aware/unaware villagers with and without bypass and inspect all PDC values.
3. Toggle back on and confirm the marker key remains with false value.
4. Trade with untouched, marker-true and marker-false villagers.
5. Test cancelled events from other plugins around NORMAL priority.
6. Put player and villager in different GriefPrevention claim contexts.
7. Verify first-interaction restock, both default thresholds, next-day calculation and empty/invalid lists.
8. Cross each XP threshold and observe the 5-second awareness window and 100-tick callback.
9. Change awareness during that callback and inspect the final state.
10. Test direct Zombie damage and other damage categories.
11. Disable/reload with unaware villagers and inspect persisted PDC/awareness.
12. Move/copy villagers with production tools and inspect PDC preservation.

## Source map

- Defaults/messages/lifecycle: `features/villageroptimizer/VillagerOptimizer.java`
- PDC, toggle and cooldown: `features/villageroptimizer/internal/VillagerAIHandler.java`
- Restock thresholds: `features/villageroptimizer/internal/VillagerRestockHandler.java`
- Level-up window: `features/villageroptimizer/internal/VillagerLevelHandler.java`
- Events and GriefPrevention: `features/villageroptimizer/listener/VillagerEventListener.java`
