# AutoPickup

> Paper · Feature name `AutoPickup` · package `features.autopickup` · disabled by default

AutoPickup is a persistent player preference that moves finalized item drops from a direct player block break into the player's normal 36-slot storage inventory. It preserves exact overflow on the original item entities, does not collect indirect destruction, and never performs database work in the block-drop hot path.

## Player behavior

When enabled, an eligible `BlockDropItemEvent` is processed after Paper and earlier plugins have finalized its item entities. Compatible partial stacks are filled before empty storage slots. Armor, offhand, crafting, cursor and result slots are never used.

For every handled drop the feature enforces:

```text
original amount = inserted amount + amount left on the ground
```

When only part of a stack fits, the original event item entity remains in place with the exact remainder. Its position, velocity, pickup delay, ownership, persistent data and other entity state are retained. AutoPickup never removes an item and spawns a replacement remainder.

Experience is unaffected and follows normal Paper block-break behavior.

## Command and permission

Permission:

```text
serverfeatures.feature.autopickup.use
```

The permission controls both command access and actual collection. Removing it suspends AutoPickup without deleting the persisted preference.

| Command | Behavior |
|---|---|
| `/autopickup` | Toggles the loaded preference. |
| `/autopickup on` | Enables idempotently. |
| `/autopickup off` | Disables idempotently. |
| `/autopickup toggle` | Explicit toggle form. |
| `/autopickup status` | Reports enabled/disabled, loading and unconfirmed-save state. |

The command is player-only and uses Paper's Brigadier command tree. There are no aliases or staff-targeting forms.

A command submitted while the database preference is loading is retained as the latest pending intent. Explicit `on` or `off` can recover from a preference-load failure; an ambiguous toggle is rejected when the previous value could not be established.

## Direct-break ownership

AutoPickup listens only to:

```text
BlockDropItemEvent @ HIGHEST, ignoreCancelled=true
```

It does not listen to explosion, piston, fluid, fire, leaf decay, physics, entity death, manual item drop, WorldEdit or generic world item events. TNT ignited by a player and later explosion-chain drops therefore remain on the ground.

### `STRICT_DIRECT`

The default scope accepts item entities whose block-coordinate origin matches:

- the saved directly broken block state;
- the counterpart of a `Bisected` structure such as a door or tall plant;
- the other bed half derived from bed part and facing.

Off-origin secondary drops, such as a torch or wall sign removed because its support was mined, remain on the ground. Ambiguous custom drops fail closed and remain untouched.

### `EVENT_ALL`

This compatibility mode accepts every item entity Paper included in that direct player's `BlockDropItemEvent`, including secondary blocks attributed to the event.

Paper does not expose a definitive source block for every individual item entity. `STRICT_DIRECT` is therefore intentionally conservative rather than guessing ownership.

## Transfer transaction

`AutoPickupTransferPlanner` works on detached clones only:

1. clone current storage;
2. process eligible drops in event order;
3. merge compatible partial stacks;
4. fill empty storage slots;
5. honor the lower of item and inventory maximum stack size;
6. produce exact per-drop remainders;
7. validate conservation before any live mutation.

`AutoPickupTransferCommitter` then snapshots the live storage, complete event item order and every original event stack before applying the plan.

If any live mutation or verification fails, it restores:

- the original player storage;
- the original event list membership and order;
- each original item entity stack.

A successful rollback leaves normal ground drops. If rollback itself fails, the player's AutoPickup is disabled for the current session and a severe diagnostic path is surfaced rather than continuing to risk corruption.

No native pickup event is fabricated. Plugins listening for physical entity pickup should not treat AutoPickup as a normal collision pickup.

## Configuration

File:

```text
plugins/ServerFeatures/features/AutoPickup/config.yml
```

| Key | Default | Meaning |
|---|---:|---|
| `enabled` | `false` | Loads the feature, ORM state, listeners and command. |
| `default-enabled` | `false` | Effective preference when no row exists. A row is created lazily on the first explicit change. |
| `drop-policy.scope` | `STRICT_DIRECT` | `STRICT_DIRECT` or `EVENT_ALL`. |
| `drop-policy.worlds.mode` | `BLACKLIST` | `BLACKLIST` or `WHITELIST`. |
| `drop-policy.worlds.values` | `[]` | Case-insensitive world names. Blank values are rejected. |
| `drop-policy.allowed-game-modes` | `SURVIVAL`, `ADVENTURE` | Exact Bukkit game-mode names. The list cannot be empty. |
| `notification.inventory-full.enabled` | `true` | Enables the targeted overflow actionbar. |
| `notification.inventory-full.notify-on-partial` | `true` | Also notify when some, but not all, eligible items fitted. |
| `notification.inventory-full.cooldown-millis` | `3000` | Per-player monotonic warning cooldown. |
| `notification.inventory-full.duration-seconds` | `2` | Targeted ActionBars override duration. |
| `persistence.retry.attempts` | `3` | Total write attempts, at least one. |
| `persistence.retry.initial-delay-millis` | `250` | First retry delay. |
| `persistence.retry.maximum-delay-millis` | `2000` | Backoff cap; cannot be below the initial delay. |
| `persistence.shutdown-drain-timeout-millis` | `1000` | Bounded wait for already-running ORM attempts during feature shutdown. |
| `diagnostics.warning-cooldown-millis` | `30000` | Per-player transfer-error log cooldown. |

Configuration is validated and converted to one immutable settings snapshot at initialization. Invalid scopes, modes, game modes and negative timings fail feature startup rather than producing partial runtime behavior. Feature reload is required to apply changes.

## Inventory-full feedback

Overflow is determined from actual planned remainders, never from `firstEmpty()`. An inventory with no empty slot may still accept an item into a partial compatible stack.

At most one message is emitted for the complete block-drop event. Variables:

- `{remaining_amount}` — total eligible item amount left;
- `{remaining_stacks}` — number of eligible event stacks with a remainder.

The cooldown uses `System.nanoTime()`, so wall-clock adjustments do not reset or extend it.

The shared ActionBars API now supports targeted timed overrides. `PauseMode.PAUSE_CYCLE` suppresses normal cycle frames only for the affected player while the warning is active; it does not pause the server-wide cycle for everybody.

## Persistence

Dependencies:

- DataProvider;
- DataRegistry;
- MySQL connection `autoPickupOrmConnection` using `player_data_rw`.

Table:

```text
player_auto_pickup_settings
```

| Column | Type/role |
|---|---|
| `player_id` | Canonical DataRegistry player ID and primary key. |
| `enabled` | Persisted boolean preference. |
| `updated_at` | Epoch milliseconds for diagnostics. |

The preference is network-scoped: any backend using the same player database restores the same value.

### Load ordering

On join or feature enable for an already-online player:

1. create an inactive `LOADING` runtime state;
2. wait for canonical DataRegistry identity;
3. load ORM state through the feature-scoped asynchronous scheduler;
4. return to the Paper main thread;
5. apply only when the same runtime-state object and generation are still current.

Quit, relog, feature reload and newer commands invalidate stale completions.

### Write ordering

Runtime state changes immediately after a valid command. Per-player writes are serialized and rapid changes are coalesced to the newest unsaved value. Bounded retries use exponential backoff. A final failure keeps the chosen setting active for the current session, marks it unconfirmed and tells the player it could not be stored.

The block-drop listener never calls DataRegistry or ORM.

## Lifecycle

Initialization:

1. validate configuration;
2. initialize DataProvider ownership;
3. register MySQL connection and ORM entity;
4. construct transfer, origin and preference services;
5. register player and block-drop listeners;
6. register Brigadier command;
7. initialize already-online players.

Quit removes runtime preference and notification/diagnostic state while immutable in-flight writes may finish.

Disable closes new preference activity, performs a bounded drain of already-running attempts, clears runtime maps and allows framework cleanup to unregister listeners, commands, tasks, ORM and DataProvider scopes.

## Compatibility boundaries

- **Protection plugins:** cancellation before AutoPickup's `HIGHEST` listener prevents collection. Same-priority ordering remains registration-order dependent.
- **Custom loot plugins:** items added or changed before AutoPickup are processed; items added later remain ground drops.
- **SilkSpawners:** its current direct `BlockBreakEvent` inventory grant is separate and is not intercepted or duplicated.
- **Old Drop2Inventory plugin:** must be removed or fully disabled before deployment. Running both implementations is unsupported.
- **Pickup listeners/statistics:** AutoPickup does not synthesize physical pickup events or pickup statistics.

## Tests and operational verification

Automated coverage includes detached partial/full insertion, multiple drops competing for capacity, unstackable items, defensive cloning and randomized conservation checks. Feature defaults are also covered by the repository-wide feature discovery contract.

In game, verify:

1. empty inventory and ordinary mining;
2. no empty slots but a compatible partial stack;
3. fully saturated storage;
4. Fortune and Silk Touch;
5. chests and shulker boxes;
6. doors, beds and tall plants;
7. support blocks with torches/signs under both scopes;
8. TNT, creepers, pistons and fluids remain untouched;
9. rapid toggles during initial login;
10. reconnect and backend switch persistence;
11. database outage and retry/failure messages;
12. permission removal while preference remains enabled;
13. targeted overflow warning while a normal actionbar cycle is active.
