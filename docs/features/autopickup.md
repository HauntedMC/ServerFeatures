# AutoPickup

> Paper · Feature name `AutoPickup` · package `features.autopickup` · disabled by default

AutoPickup is a persistent player preference that moves finalized item drops from a direct player block break into the player's normal 36-slot storage inventory. It preserves exact overflow on the original item entities, excludes indirect destruction, and never performs database work in the block-drop hot path.

## Player behavior

When enabled, an eligible `BlockDropItemEvent` is processed after Paper and earlier plugins have finalized its item entities. Compatible partial stacks are filled before empty storage slots. Armor, offhand, crafting, cursor and result slots are never used.

For every handled drop the feature enforces:

```text
original amount = inserted amount + amount left on the ground
```

When only part of a stack fits, the original event item entity remains in place with the exact remainder. Its position, velocity, pickup delay, ownership, persistent data and other entity state are retained. AutoPickup never removes an item and spawns a replacement remainder.

Experience is unaffected and follows normal Paper block-break behavior. A configurable pickup sound is played once per successful block-drop event, not once per item entity. Optional sound or actionbar failures are rate-limited and isolated after the item transaction, so they cannot undo or disrupt a completed transfer.

## Command and permission

Permission:

```text
serverfeatures.feature.autopickup.use
```

The permission always controls command access. With `drop-policy.require-use-permission: true`, it also controls actual collection. Removing the permission then suspends AutoPickup without deleting the persisted preference. Set the option to `false` only when command access should remain permission-gated while collection is available to every player with an enabled preference.

| Command | Behavior |
|---|---|
| `/autopickup` | Toggles the loaded preference. |
| `/autopickup on` | Enables idempotently. |
| `/autopickup off` | Disables idempotently. |
| `/autopickup toggle` | Explicit toggle form. |
| `/autopickup status` | Reports enabled/disabled, loading and unconfirmed-save state. |

The command is player-only and uses Paper's Brigadier command tree. There are no aliases or staff-targeting forms.

Commands submitted while the database preference is loading are composed in order: explicit `on` or `off` supersedes older relative intent, while repeated toggles preserve their real parity. Explicit `on` or `off` can recover from a preference-load failure; an ambiguous toggle is rejected when the previous value could not be established. Repeating explicit `on` or `off` after a failed save retries persistence instead of only reporting that the state was already selected.

## Direct-break ownership

AutoPickup listens only to:

```text
BlockDropItemEvent @ HIGHEST, ignoreCancelled=true
```

It does not listen to explosion, piston, fluid, fire, leaf decay, physics, entity death, manual item drop, WorldEdit or generic world-item events. TNT ignited by a player and later explosion-chain drops therefore remain on the ground.

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

1. capture a detached baseline of current storage;
2. process eligible drops in event order;
3. merge compatible partial stacks;
4. fill empty storage slots;
5. honor the lower of item and inventory maximum stack size;
6. produce exact per-drop remainders;
7. validate per-drop and aggregate conservation before any live mutation.

`AutoPickupTransferCommitter` then snapshots the live storage, complete event item order and every original event stack. Before mutation it verifies that:

- storage still matches the plan baseline;
- every eligible entity is present exactly once by identity;
- no eligible entity stack changed after planning.

After mutation it verifies both the final inventory and the exact event membership, order and remainder stacks. If any ordinary runtime mutation or verification fails, it restores:

- the original player storage;
- the original event-list membership and order;
- each original item entity stack.

A successful rollback leaves normal ground drops. If inventory restoration itself fails but the exact planned inventory survived, AutoPickup completes the matching event-side commit instead of restoring duplicate ground items. Any rollback that cannot be fully confirmed disables AutoPickup for the player's current session and surfaces a severe diagnostic. Fatal JVM errors are not swallowed by the transaction wrapper.

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
| `drop-policy.require-use-permission` | `true` | Require the use permission during collection as well as for the command. |
| `notification.inventory-full.enabled` | `true` | Enables the targeted overflow actionbar. |
| `notification.inventory-full.notify-on-partial` | `true` | Also notify when some, but not all, eligible items fitted. |
| `notification.inventory-full.cooldown-millis` | `3000` | Per-player monotonic warning cooldown. |
| `notification.inventory-full.duration-seconds` | `2` | Targeted ActionBars override duration, from `0` through `60`. |
| `effects.pickup-sound.enabled` | `true` | Plays a sound after a successful transfer. |
| `effects.pickup-sound.sound` | `minecraft:entity.item.pickup` | Namespaced sound key; custom resource-pack keys are allowed. |
| `effects.pickup-sound.category` | `PLAYERS` | Bukkit `SoundCategory`. |
| `effects.pickup-sound.volume` | `0.2` | Finite volume from `0` through `16`. |
| `effects.pickup-sound.pitch` | `1.0` | Finite pitch from `0.5` through `2.0`. |
| `persistence.retry.attempts` | `3` | Total write attempts, from `1` through `10`. |
| `persistence.retry.initial-delay-millis` | `250` | First retry delay, from `0` through `60000`. |
| `persistence.retry.maximum-delay-millis` | `2000` | Backoff cap, from `0` through `60000` and not below the initial delay. |
| `persistence.join-recheck-delay-millis` | `3000` | One delayed, generation-fenced read after login to catch a write finishing on the previous backend. `0` disables it; maximum `60000`. |
| `persistence.shutdown-drain-timeout-millis` | `1000` | Bounded wait for already-running ORM attempts during feature shutdown; maximum `10000`. |
| `diagnostics.warning-cooldown-millis` | `30000` | Per-player transfer/feedback error log cooldown. |

Configuration is validated and converted to one immutable settings snapshot at initialization. Invalid scopes, modes, game modes, blank world names, invalid sound values, timing overflow and unsafe scheduler or timeout ranges fail feature startup rather than producing partial runtime behavior. Feature reload is required to apply changes.

## Inventory-full feedback

Overflow is determined from actual planned remainders, never from `firstEmpty()`. An inventory with no empty slot may still accept an item into a partial compatible stack.

At most one message is emitted for the complete block-drop event. Variables:

- `{remaining_amount}` — total eligible item amount left;
- `{remaining_stacks}` — number of eligible event stacks with a remainder.

The cooldown uses `System.nanoTime()`, so wall-clock adjustments do not reset or extend it.

The shared ActionBars API supports targeted timed overrides. `PauseMode.PAUSE_CYCLE` suppresses normal cycle frames only for the affected player while the warning is active; it does not pause the server-wide cycle for everybody. The new targeted API methods have safe interface defaults, so existing third-party `ActionBarAPI` implementations remain source and binary compatible.

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
| `write_revision` | Monotonic request revision used to reject stale cross-backend writes. |

The preference is network-scoped: any backend using the same player database restores the same value.

### Required schema

With the framework's recommended production ORM mode `validate`, provision the table before enabling the feature:

```sql
CREATE TABLE player_auto_pickup_settings (
    player_id BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL,
    updated_at BIGINT NOT NULL,
    write_revision BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (player_id)
) ENGINE=InnoDB;
```

Alternatively, an explicitly managed deployment may temporarily use the framework global `dataprovider_orm_schema_mode: update` to let Hibernate create or update the table. `validate` is safer for normal production startup because unexpected schema drift causes the feature to fail closed.

The `player_data_rw` DataProvider access policy must share the connection with `ServerFeatures`; otherwise ORM initialization is intentionally rejected.

### Load ordering

On join or feature enable for an already-online player:

1. create an inactive `LOADING` runtime state;
2. wait for canonical DataRegistry identity;
3. load ORM state through the feature-scoped asynchronous scheduler;
4. return to the Paper main thread;
5. apply only when the same runtime-state object and generation are still current;
6. perform one configurable delayed recheck to catch a preference write that completed on the previous backend just after the initial read.

Quit, relog, feature reload, local writes and newer commands invalidate stale completions. The delayed recheck is skipped while a local write is active and only applies a strictly newer database revision.

### Write ordering

Runtime state changes immediately after a valid command. Each request captures the canonical player ID and receives a globally comparable, process-monotonic revision at request time. Per-player writes are serialized and rapid changes are coalesced to the newest unsaved request. The MySQL upsert changes the stored preference only when the incoming revision is newer; an old backend can therefore finish late without overwriting a newer command from another backend.

The write path reads back the authoritative row in the same transaction after clearing Hibernate's managed state. If another backend won, the local runtime reconciles to that newer value. Bounded retries use exponential backoff. A final failure keeps the chosen setting active for the current session, marks it unconfirmed and tells the player it could not be stored.

The block-drop listener never calls DataRegistry or ORM.

## Lifecycle

Initialization:

1. validate configuration;
2. initialize DataProvider ownership;
3. register MySQL connection and validate the ORM entity/schema;
4. construct transfer, origin and preference services;
5. register player and block-drop listeners;
6. register Brigadier command;
7. initialize already-online players.

Quit removes runtime preference and notification/diagnostic state while immutable in-flight writes may finish. A fast relog on the same backend carries the newest local in-flight request into the replacement session rather than reloading stale database state.

Disable closes new preference activity, performs an interrupt-safe bounded drain of already-running attempts, clears runtime maps and allows framework cleanup to unregister listeners, commands, delayed rechecks, ORM and DataProvider scopes.

## Compatibility boundaries

- **Protection plugins:** cancellation before AutoPickup's `HIGHEST` listener prevents collection. Same-priority ordering remains registration-order dependent.
- **Custom loot plugins:** items added or changed before AutoPickup are processed; items added later remain ground drops.
- **SilkSpawners:** its current direct `BlockBreakEvent` inventory grant is separate and is not intercepted or duplicated.
- **Old Drop2Inventory plugin:** must be removed or fully disabled before deployment. Running both implementations simultaneously is unsupported.
- **Pickup listeners/statistics:** AutoPickup does not synthesize physical pickup events or pickup statistics.

## Tests and operational verification

Automated coverage includes:

- detached partial/full insertion and custom stack limits;
- multiple drops competing for capacity;
- defensive cloning and randomized per-drop/aggregate conservation;
- inventory changes between planning and commit;
- silent event-stack corruption detection;
- complete inventory/event/stack rollback and conservation-preserving rollback fallback;
- strict direct, bisected and bed-origin classification;
- composed command intent while preference loading;
- scheduler-facing configuration bounds and retry backoff;
- monotonic and observed cross-backend write revisions;
- backward-compatible targeted ActionBar defaults;
- a bundled Paper/MySQL acceptance boot with AutoPickup enabled, scoped DataProvider access and the production table pre-provisioned.

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
10. immediate backend switch after toggling;
11. database outage and retry/failure messages;
12. permission removal while preference remains enabled;
13. targeted overflow warning while a normal actionbar cycle is active;
14. pickup sound enabled, disabled and customized.
