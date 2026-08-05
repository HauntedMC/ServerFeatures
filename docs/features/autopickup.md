# AutoPickup

> Paper · Feature name `AutoPickup` · package `features.autopickup` · disabled by default

AutoPickup is a local, PDC-persisted player preference that moves finalized item drops from a direct player block break into the player's normal storage inventory and, when storage cannot accept more, the offhand slot. It preserves exact overflow on the original item entities, excludes indirect destruction, and has no database or DataRegistry integration.

## Player behavior

When enabled, an eligible `BlockDropItemEvent` is processed after Paper and earlier plugins have finalized its item entities. Insertion order is:

1. compatible partial stacks in the 36 normal storage slots;
2. empty normal storage slots;
3. a compatible partial offhand stack or an empty offhand slot.

The offhand is strictly a final fallback. An incompatible offhand item is never replaced. Armor, crafting, cursor and result slots are never used.

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

The permission always controls command access. With `drop-policy.require-use-permission: true`, it also controls actual collection. Removing the permission suspends AutoPickup without deleting the persisted preference.

| Command | Behavior |
|---|---|
| `/autopickup` | Toggles the loaded preference. |
| `/autopickup on` | Enables idempotently. |
| `/autopickup off` | Disables idempotently. |
| `/autopickup toggle` | Explicit toggle form. |
| `/autopickup status` | Reports the current enabled/disabled preference. |

The command is player-only and uses Paper's Brigadier command tree. There are no aliases or staff-targeting forms.

Preference reads and writes are synchronous main-thread PDC operations. There is no loading state, command queue, save retry, or remote preference reconciliation.

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

1. capture detached baselines of normal storage and offhand;
2. process eligible drops in event order;
3. merge compatible partial storage stacks;
4. fill empty storage slots;
5. use compatible or empty offhand capacity last;
6. honor the lower of item and inventory maximum stack size;
7. produce exact per-drop remainders;
8. validate per-drop and aggregate conservation before any live mutation.

`AutoPickupTransferCommitter` snapshots live storage, offhand, complete event item order and every original event stack. Before mutation it verifies that:

- storage still matches the plan baseline;
- offhand still matches the plan baseline;
- every eligible entity is present exactly once by identity;
- no eligible entity stack changed after planning.

After mutation it verifies final storage, final offhand, exact event membership/order and exact remainder stacks. If any ordinary runtime mutation or verification fails, it restores:

- original player storage;
- original offhand;
- original event-list membership and order;
- every original item entity stack.

A successful rollback leaves normal ground drops. If inventory restoration itself fails but the exact planned storage and offhand survived, AutoPickup completes the matching event-side commit instead of restoring duplicate ground items. Any rollback that cannot be fully confirmed disables AutoPickup for the player's current session and surfaces a severe diagnostic. Fatal JVM errors are not swallowed.

No native pickup event is fabricated. Plugins listening for physical entity pickup should not treat AutoPickup as a normal collision pickup.

## Configuration

File:

```text
plugins/ServerFeatures/features/AutoPickup/config.yml
```

| Key | Default | Meaning |
|---|---:|---|
| `enabled` | `false` | Loads the feature, PDC preference state, listeners and command. |
| `default-enabled` | `false` | Effective preference when the player has no PDC choice. |
| `drop-policy.scope` | `STRICT_DIRECT` | `STRICT_DIRECT` or `EVENT_ALL`. |
| `drop-policy.worlds.mode` | `BLACKLIST` | `BLACKLIST` or `WHITELIST`. |
| `drop-policy.worlds.values` | `[]` | Case-insensitive world names. Blank values are rejected. |
| `drop-policy.allowed-game-modes` | `SURVIVAL`, `ADVENTURE` | Exact Bukkit game-mode names. The list cannot be empty. |
| `drop-policy.require-use-permission` | `true` | Require the use permission during collection as well as for the command. |
| `notification.inventory-full.enabled` | `true` | Enables the targeted inventory-full actionbar. |
| `notification.inventory-full.notify-on-partial` | `true` | Also notify when some eligible items fitted but overflow remains. |
| `notification.inventory-full.cooldown-millis` | `3000` | Per-player monotonic warning cooldown. |
| `notification.inventory-full.duration-seconds` | `2` | Targeted ActionBars override duration, from `0` through `60`. |
| `effects.pickup-sound.enabled` | `true` | Plays a sound after a successful transfer. |
| `effects.pickup-sound.sound` | `minecraft:entity.item.pickup` | Namespaced sound key; custom resource-pack keys are allowed. |
| `effects.pickup-sound.category` | `PLAYERS` | Bukkit `SoundCategory`. |
| `effects.pickup-sound.volume` | `0.2` | Finite volume from `0` through `16`. |
| `effects.pickup-sound.pitch` | `1.0` | Finite pitch from `0.5` through `2.0`. |
| `diagnostics.warning-cooldown-millis` | `30000` | Per-player transfer/feedback error log cooldown. |

Configuration is validated and converted to one immutable settings snapshot at initialization. Invalid scopes, modes, game modes, blank world names, invalid sound values and timing overflow fail feature startup. Feature reload is required to apply changes.

## Inventory-full feedback

Overflow is determined from actual planned remainders after both normal storage and offhand capacity have been considered. It is never inferred from `firstEmpty()`.

At most one simple actionbar is emitted for the complete block-drop event:

```text
Je inventaris zit vol.
```

The message intentionally contains no item or stack counts. The cooldown uses `System.nanoTime()`, so wall-clock adjustments do not reset or extend it.

The shared ActionBars API supports targeted timed overrides. `PauseMode.PAUSE_CYCLE` suppresses normal cycle frames only for the affected player while the warning is active; it does not pause the server-wide cycle.

## Persistence

The preference is stored in the player's Bukkit `PersistentDataContainer` under:

```text
serverfeatures:autopickup_enabled
```

The value is a byte: `1` means enabled and `0` means disabled. A missing, incompatible, or invalid value uses `default-enabled`. Both boolean values are stored explicitly so a player can remain opted out when `default-enabled` is `true`.

Paper persists player PDC with its ordinary local playerdata, so the choice normally survives logout, a clean restart, and feature reload. It is intentionally local to this Paper server: changing backend does not carry or reconcile the setting unless the servers share playerdata through some external mechanism. As ordinary non-critical playerdata, the most recent change can be lost after a crash before Paper flushes the player.

AutoPickup no longer initializes DataProvider, resolves DataRegistry identities, creates an ORM context, or reads/writes a database table. The obsolete `persistence` configuration section is removed when the feature initializes. Existing `player_auto_pickup_settings` rows are not migrated or read. After deploying this version, the obsolete table can be dropped when no older server still uses it; players begin from their local PDC choice or `default-enabled`.

## Lifecycle

Initialization validates configuration, constructs services, registers listeners/command and initializes already-online players directly from PDC.

Commands update runtime state and the player's PDC immediately. Quit removes only the runtime cache and diagnostic state; Paper owns playerdata flushing.

Disable clears runtime caches and allows framework cleanup to unregister listeners and commands. There are no asynchronous preference operations or database scopes to drain.

## Compatibility boundaries

- **Protection plugins:** cancellation before AutoPickup's `HIGHEST` listener prevents collection. Same-priority ordering remains registration-order dependent.
- **Custom loot plugins:** items added or changed before AutoPickup are processed; items added later remain ground drops.
- **SilkSpawners:** its direct `BlockBreakEvent` inventory grant is separate and is not intercepted or duplicated.
- **Old Drop2Inventory plugin:** must be removed or fully disabled. Running both implementations is unsupported.
- **Pickup listeners/statistics:** AutoPickup does not synthesize physical pickup events or pickup statistics.

## Tests and operational verification

Automated coverage includes:

- storage-first insertion and empty/compatible/incompatible offhand behavior;
- full and partial insertion with exact overflow;
- custom and unstackable item limits;
- multiple drops competing for storage and offhand capacity;
- randomized per-drop and aggregate conservation;
- storage, offhand and entity changes between planning and commit;
- silent event-stack corruption detection;
- complete storage/offhand/event rollback and conservation-preserving fallback;
- strict direct, bisected and bed-origin classification;
- missing-PDC default selection and explicit true/false restoration;
- command writes to the player PDC and session-only safety disable behavior;
- backward-compatible targeted ActionBar defaults;
- bundled Paper startup and clean shutdown with AutoPickup enabled.

In game, verify:

1. empty storage and ordinary mining;
2. compatible partial storage stacks;
3. full storage with an empty offhand;
4. full storage with a compatible partial offhand stack;
5. full storage with an incompatible or full offhand item;
6. Fortune and Silk Touch;
7. chests, shulker boxes, doors, beds and tall plants;
8. support blocks with torches/signs under both scopes;
9. TNT, creepers, pistons and fluids remain untouched;
10. enable/disable/status followed by logout, restart and feature reload;
11. backend switching does not synchronize the preference;
12. `default-enabled: true` while a player has explicitly selected off;
13. permission removal while preference remains enabled;
14. targeted overflow warning while a normal actionbar cycle is active;
15. pickup sound enabled, disabled and customized.
