# RepairNPC

> Paper · Feature ID `repairnpc` · disabled by default · Citizens trait `repair`

RepairNPC registers a Citizens trait that turns any NPC carrying that trait into a Vault-backed blacksmith. Players present the item in their main hand, click once to receive a quoted price, and click the same NPC again with the same item to accept. The feature then withdraws the price, temporarily moves the item into the NPC's hand, waits for the configured repair delay, resets item damage to zero, and returns or drops the repaired item.

This page documents the implementation as it exists. In particular, RepairNPC is a local, in-memory interaction system: sessions, cooldowns and in-progress items are not persisted, there is no recovery journal, and the economy withdrawal plus delayed item return are not one atomic transaction.

## Dependencies and lifecycle

RepairNPC directly links against:

- **Citizens**, for trait registration and NPC click events;
- **Vault**, for the `Economy` service;
- an installed Vault-compatible economy provider.

When the feature initializes it stores a static feature instance, creates `TraitInfo` for `RepairTrait` under the name `repair`, and registers the trait only when Citizens does not already have a trait with that name. On disable it deregisters the stored trait when Citizens reports that `repair` exists.

Each Citizens NPC to be used as a blacksmith must have the `repair` trait attached through Citizens. There is no configured NPC UUID, name or location: trait membership is the complete NPC selection mechanism.

A `RepairTrait` obtains the Vault economy provider in its constructor. If no provider is registered, the feature logs a severe error, but the trait remains present and later interaction paths still dereference the economy field. Operationally, Vault and its provider must therefore be ready before repair NPC traits are instantiated.

## Permission and interaction surface

There is no command and no PlaceholderAPI expansion.

| Permission | Effect |
|---|---|
| `serverfeatures.feature.repairnpc.use` | Allows left- and right-click interaction with NPCs carrying the `repair` trait. |

Players without the permission receive no response. The NPC interaction event is not explicitly cancelled by this feature.

### Left click

A permitted left click first applies the cooldown check. When the held material name ends in `_SWORD` or `_AXE`, or is `TRIDENT`, the feature damages the Citizens NPC entity by one point and sends `repairnpc.auw`; it does not begin a repair quote. Other held items use the normal repair flow.

Because the suffix test includes pickaxes only when their material name ends in `_AXE`—which it does not—pickaxes are quoted normally. Ordinary axes and swords must be presented through right click rather than left click.

### Right click

A permitted right click always uses the main-hand item for the repair flow.

## Configuration

Feature configuration is generated beneath the RepairNPC feature directory.

### Price maps

Material lookup keys are derived from the Bukkit material enum name by converting to lower case and replacing `_` with `-`. Examples are `diamond-pickaxe`, `trident` and `elytra`.

| Key | Default | Meaning |
|---|---:|---|
| `base-prices.default` | `100` | Base price used when the material-specific key is absent. |
| `base-prices.trident` | `400` | Trident base price override. |
| `base-prices.elytra` | `400` | Elytra base price override. |
| `price-per-durability-point.default` | `0.3` | Cost for each current damage point when no material override exists. |
| `price-per-durability-point.trident` | `0.6` | Trident damage-point price. |
| `price-per-durability-point.elytra` | `0.6` | Elytra damage-point price. |
| `enchantment-modifiers.default` | `25` | Per-enchantment-level modifier when no enchantment key exists. |
| `enchantment-modifiers.trident` | `50` | Present in the defaults, but enchantment lookup is by enchantment key, not material. This key therefore affects an enchantment literally named `trident`, not all tridents. |
| `enchantment-modifiers.elytra` | `50` | Likewise interpreted as an enchantment key and normally unused. |

Any material or enchantment can be added to these maps. Enchantment lookup first checks the full namespaced key such as `minecraft:sharpness`, then the bare key `sharpness`, then `default`. Underscores are replaced by hyphens in both forms.

The exact quote is:

```text
base price
+ current item damage × price per durability point
+ sum(enchantment modifier × enchantment level)
```

`Damageable#getDamage()` is the number of durability points already consumed, so an undamaged but eligible item still costs its base price and enchantment modifiers. The result is not rounded by RepairNPC; Vault's formatter controls how the quoted value is displayed.

### Operational flags and timing

| Key | Default | Meaning |
|---|---:|---|
| `dropitem` | `false` | After a delayed repair, drop the repaired item naturally at the NPC instead of adding it to the player's inventory. Ignored when delay is disabled. |
| `disablecooldown` | `false` | Removes the player's cooldown entry at the start of every click, effectively disabling cooldown enforcement. |
| `disabledelay` | `false` | Schedules the repair with zero delay and returns the item directly to the player's main hand. |
| `delays-in-seconds.minimum` | `4` | Inclusive minimum random repair delay. |
| `delays-in-seconds.maximum` | `8` | Inclusive maximum random repair delay. |
| `delays-in-seconds.reforge-cooldown` | `8` | Cooldown applied after a successful repair completes. |

The delay is selected with `Random#nextInt(maximum - minimum + 1) + minimum` and converted to ticks. `maximum` must be greater than or equal to `minimum`; an inverted range causes an exception when repair work starts. Negative values are not validated by this class.

Config values are copied into each `RepairTrait` instance when that trait object is created. Editing the file does not mutate already-instantiated trait fields until the Citizens trait/feature lifecycle creates new instances.

## Eligible items

The item must be in the main hand and be one of the hard-coded materials below.

### Tools

- all wooden, stone, golden, iron, diamond and netherite pickaxes, shovels, hoes, swords and axes;
- bow, crossbow and trident;
- flint and steel, fishing rod, shears;
- carrot on a stick and warped fungus on a stick.

### Armour and defensive equipment

- leather, chainmail, golden, iron, diamond and netherite armour;
- turtle helmet;
- shield;
- elytra.

The eligibility table is source-owned and has no configuration extension point. An eligible item does not have to be damaged. Other damageable items are rejected even when Bukkit exposes damage metadata for them.

## Session and confirmation state machine

State belongs to one `RepairTrait` instance, which normally means one Citizens NPC. Each trait has exactly one `session` field; therefore one blacksmith NPC can quote or repair for only one player at a time.

### First click: quote

1. If `disablecooldown` is true, remove the player's cooldown entry.
2. Require the use permission.
3. Reject an unexpired cooldown.
4. Clear the current session only when it is more than ten seconds old or the new clicking player is more than twenty blocks from the NPC.
5. If another player's session remains, send `repairnpc.busy-with-player`.
6. Validate the main-hand material against the hard-coded tool/armour lists.
7. Calculate the current price and require the current Vault balance to be at least that amount.
8. Store a `RepairSession` containing the player, NPC and the current `ItemStack` reference.
9. Send `repairnpc.cost` with `{price}` and `{item}`.

The quoted session does not reserve funds or lock the inventory slot. It expires lazily: there is no expiry task, so it is removed only on a later click that observes the ten-second/distance condition.

### Second click: acceptance

For the same player and same NPC:

1. If the delayed repair task is queued, send `repairnpc.busy-with-reforge`.
2. Compare the originally captured `ItemStack` with the player's current main-hand stack using `ItemStack#equals`.
3. Recalculate the price from the current main-hand item and check the balance again.
4. On mismatch or insufficient funds, send the relevant message and discard the session.
5. Otherwise send `repairnpc.start-reforge`, call Vault withdrawal, mark the session as running, copy the held item to the NPC's equipment and clear the player's main hand.

The withdrawal response is not inspected. A provider-side failed or partial withdrawal does not stop the repair after `withdrawPlayer` returns.

## Delayed repair completion

When the scheduled task runs:

1. Obtain the item's `ItemMeta` and cast it to Bukkit's damageable item meta.
2. Set damage to zero and write the metadata back.
3. Send `repairnpc.successful-reforge`.
4. Clear the Citizens NPC's main hand.
5. Return the item:
   - with delay enabled and `dropitem=false`, call `PlayerInventory#addItem`;
   - with delay enabled and `dropitem=true`, drop it naturally at the NPC;
   - with delay disabled, replace the player's current main-hand slot.
6. Add the configured cooldown unless disabled.
7. Clear the trait's session.

The leftovers returned by `addItem` are ignored. If the player's inventory is full, Bukkit may leave items in the returned map without RepairNPC dropping or otherwise recovering them. The completion task also assumes the player, NPC entity and their worlds remain usable; there is no explicit quit, NPC despawn, world unload or feature-disable recovery path.

## Cooldown semantics

Cooldowns are stored in a `Map<String, Calendar>` keyed by the player's current name rather than UUID. They are:

- local to one trait/NPC instance;
- created only after successful completion;
- not persisted across restart/reload or trait recreation;
- not shared between multiple repair NPCs;
- checked on both left and right click;
- removed after expiry when the player next clicks.

A player can therefore use another repair NPC immediately, and a name change can leave an unreachable old entry until the trait is discarded.

## Messages and variables

| Message key | Variables / condition |
|---|---|
| `repairnpc.auw` | Left-clicking with a sword, axe or trident. |
| `repairnpc.busy-with-player` | This NPC has another player's active quote/repair session. |
| `repairnpc.busy-with-reforge` | The same player's delayed task is still queued. |
| `repairnpc.cooldown-not-expired` | Cooldown exists; no remaining-time variable is supplied. |
| `repairnpc.cost` | `{price}` from `Economy#format`; `{item}` is the lower-case material name with underscores replaced by spaces. |
| `repairnpc.invalid-item` | Main-hand material is outside the hard-coded lists. |
| `repairnpc.item-changed-during-reforge` | Confirmation stack no longer equals the quoted stack. |
| `repairnpc.start-reforge` | Confirmation accepted before withdrawal/item removal. |
| `repairnpc.successful-reforge` | Delayed completion ran. |
| `repairnpc.insufficient-funds` | Balance was below the calculated price during quote or confirmation. |
| `repairnpc.fail-reforge` | Defined in the default messages but not sent by the current implementation. |

## Persistence, messaging and APIs

RepairNPC has:

- no database entity or DataRegistry dependency;
- no Redis or plugin-messaging contract;
- no PlaceholderAPI expansion;
- no public ServerFeatures service API;
- no persisted sessions, cooldowns, quotes or pending items;
- no audit log for economy withdrawals or repaired items.

Citizens stores that the NPC has the trait, but `RepairTrait#load` and `save` deliberately store no trait-specific data.

## Event ordering and thread model

Citizens invokes `NPCLeftClickEvent` and `NPCRightClickEvent` handlers at the default event priority. The handlers do not specify `ignoreCancelled`, do not cancel the click and perform economy, inventory and NPC mutations synchronously on the event thread. Repair completion is scheduled through the feature task manager on the normal Bukkit scheduler.

There is no synchronization around the trait's `HashMap`, session field or task state; the implementation relies on Citizens/Bukkit interaction and scheduled callbacks being main-thread work.

## Operational limitations and failure windows

The most important boundaries are:

- **Withdrawal-to-return gap:** money is withdrawn and the item is removed before the delayed task. A shutdown, disable, task cancellation, NPC removal or exception in completion can strand the item without automatic reimbursement.
- **No transaction verification:** the Vault withdrawal result is ignored.
- **One session per NPC:** even an unconfirmed quote blocks everyone else until it is invalidated by a later click.
- **Full inventory loss risk:** ignored `addItem` leftovers can leave the returned item unhandled.
- **Offline player handling:** the stored `Player` object is used later without an online check.
- **NPC availability:** the task reads and mutates `npc.getEntity()` without checking whether it is spawned.
- **Zero-delay overwrite:** with `disabledelay=true`, completion writes the repaired item to the main hand; an item placed there between removal and callback can be replaced.
- **No feature-owned cleanup:** `disable()` deregisters the trait but does not explicitly refund or return active session items.
- **Price race:** the quote is informational; the second click recalculates against the current item/config. There is no fixed quoted-price guarantee.

These are implementation facts to account for during operations and future redesigns, not guarantees of loss in every interruption.

## Verification checklist

1. Attach the `repair` trait to a Citizens NPC and confirm a player without permission gets no repair dialogue.
2. Quote and repair one damaged unenchanted item; verify the exact formula and Vault balance change.
3. Repeat with multiple enchantments and material/enchantment-specific config overrides.
4. Confirm left-click sword/axe/trident damages the NPC and right click starts the quote.
5. Change the held item after the quote and verify the session is cancelled without withdrawal.
6. Let another player click during a quote and during the queued repair.
7. Test minimum/maximum delay boundaries and cooldown expiry on the same NPC and a second NPC.
8. Test `dropitem`, `disabledelay` and `disablecooldown` independently.
9. Fill the player's inventory before completion and observe the provider/Bukkit return behaviour.
10. Disconnect the player, despawn the NPC and disable/reload the feature during an active repair in a controlled environment; confirm the documented absence of recovery before production rollout.

## Source map

- Feature and defaults: `features/repairnpc/RepairNPC.java`
- Citizens interaction/session lifecycle: `features/repairnpc/hook/RepairTrait.java`
- Price formula and Vault operations: `features/repairnpc/util/EcoUtil.java`
- Hard-coded eligibility: `features/repairnpc/util/ItemUtil.java`
