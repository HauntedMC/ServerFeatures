# DurabilityAlert

> Paper · Feature name `DurabilityAlert` · feature package `features.durabilityalert` · disabled by default

DurabilityAlert warns a player in the action bar whenever `PlayerItemDamageEvent` is about to leave a damageable item at or below a configured remaining-durability percentage. It also plays one of two fixed note-block sounds depending on whether the calculated post-hit durability reaches zero.

The implementation is intentionally small and event-driven. It has no commands, permissions, per-item state, cooldown, hysteresis, inventory scanning, slot filters, persistence, database, messaging, API or PlaceholderAPI integration. A qualifying low-durability item can therefore alert on every damage event.

## Commands and permissions

No command is registered and no permission is checked. Every player whose uncancelled item-damage event reaches the listener is eligible.

There is no opt-out permission, staff bypass, per-world rule or player preference.

## Complete configuration reference

File: `plugins/ServerFeatures/features/DurabilityAlert/config.yml`.

| Key | Default | Exact behaviour and edge cases |
|---|---:|---|
| `enabled` | `false` | Enables handler construction and listener registration. |
| `defaultvalue` | `10` | Remaining durability threshold expressed as a percentage. The value is directly cast to `int` once during handler construction. A malformed/missing non-integer value can fail initialization. |

Threshold examples:

- `10`: warn when the current pre-event remaining durability is at or below 10%.
- `100`: every damageable item event qualifies.
- `0`: normally no warning occurs because an item with zero remaining durability generally cannot generate another normal damage event; exact behaviour depends on event timing.
- negative: effectively disables alerts because percentage cannot normally be negative.
- above 100: every normal damageable item qualifies.

There is no explicit clamp to `0..100` and no live config reread. Reload/re-enable the feature after changing the value.

## Event contract and ordering

```java
@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
public void onItemDamage(PlayerItemDamageEvent event)
```

At `HIGH`, after lower-priority item-damage listeners:

1. obtain `event.getItem()` and `event.getPlayer()`;
2. require item metadata implementing Bukkit `Damageable`;
3. require material maximum durability greater than zero;
4. read the item's current metadata damage;
5. calculate current remaining durability and percentage;
6. when percentage is at or below `defaultvalue`, send an alert.

Events cancelled before `HIGH` are ignored. A later `HIGHEST`/`MONITOR` plugin can still cancel or change the damage after the warning was sent.

### Important timing semantics

The handler does **not** use `event.getDamage()` when calculating the threshold. It reads the item's current metadata damage before Paper applies this event's durability damage:

```text
remainingBeforeEvent = maxDurability - currentMetaDamage
percentage = remainingBeforeEvent / maxDurability * 100
```

For the displayed absolute value it then subtracts exactly one:

```text
displayedRemaining = max(remainingBeforeEvent - 1, 0)
```

Consequences:

- The threshold comparison is based on durability **before** the current event.
- The displayed value assumes the event applies exactly one point of durability damage.
- `PlayerItemDamageEvent#getDamage()` may be greater than one or modified by other plugins; that value is ignored.
- Unbreaking and Paper event semantics can produce event damage different from one, so the displayed remaining value can be inaccurate.
- An item that crosses the percentage threshold because of the current hit may not alert until the next item-damage event.
- A later plugin that changes/cancels damage can make the warning inconsistent with the final item state.

For a precise post-event calculation, the implementation would need to use the final event damage at a late priority or inspect the item on the next tick.

## Eligible items

An item is processed only when:

- `item.hasItemMeta()` is true;
- `item.getItemMeta()` implements `Damageable`;
- `item.getType().getMaxDurability() > 0`.

This covers ordinary damageable tools, weapons, armor and other vanilla/Paper items represented by the damageable item component. It does not explicitly filter by:

- equipped slot;
- main/off hand;
- armor versus tool;
- custom model/data;
- unbreakable flag;
- item owner;
- world/gamemode;
- whether the item ultimately breaks.

The event itself determines which item is being damaged.

## Percentage calculation

```text
maxDurability = Material#getMaxDurability()
currentDamage = Damageable#getDamage()
remaining = maxDurability - currentDamage
percentage = remaining / maxDurability * 100.0
qualifies when percentage <= configured threshold
```

No clamp is applied to metadata damage or calculated remaining before percentage comparison. Custom plugins can create negative/out-of-range damage metadata, producing percentages outside `0..100`. The displayed value is clamped only after subtracting one.

The comparison is inclusive. An item at exactly 10.0% qualifies for threshold 10.

Because the configured threshold is an integer while percentage is a double, materials whose durability does not divide cleanly into 100 can cross at non-integer remaining counts.

## Alert output

The feature uses Adventure item display name from:

```java
item.effectiveName()
```

This preserves the effective custom/translated item name component for `{item}` rather than storing a plain material name.

### Broken/zero branch

When `displayedRemaining == 0`:

- message: `durabilityalert.no_durability` with `{item}`;
- destination: player action bar;
- sound: `BLOCK_NOTE_BLOCK_SNARE`;
- volume/pitch: `1.0 / 1.0`;
- sound location: player's current location.

This branch says the item is broken based on the assumed one-point post-event calculation. The feature does not itself break/remove the item.

### Low-durability branch

When displayed remaining is positive:

1. build `durabilityalert.low_durability` with `{item}`;
2. build `durabilityalert.durability_left` with `{durability}`;
3. append the second component to the first;
4. send as one action-bar component;
5. play `BLOCK_NOTE_BLOCK_BASEDRUM` at volume/pitch `1.0 / 1.0`.

There is no configurable output channel, sound, volume, pitch, duration or aggregation.

## Messages and variables

| Key | Variables | Purpose |
|---|---|---|
| `durabilityalert.no_durability` | `{item}` Adventure component | Zero/broken warning branch. |
| `durabilityalert.low_durability` | `{item}` Adventure component | First half of non-zero warning. |
| `durabilityalert.durability_left` | `{durability}` integer | Appended absolute remaining value. |

The messages are localized for the affected player.

## Repetition and spam characteristics

The feature stores no last-warning state. Once an item is below threshold, every subsequent qualifying `PlayerItemDamageEvent` produces:

- a new action-bar update;
- a sound.

There is no:

- one-time threshold crossing detection;
- per-item UUID/PDC identity;
- cooldown;
- reminder interval;
- lower threshold tiers;
- repair/reset tracking;
- quit cleanup because no state exists.

For fast tools or armor under repeated damage, this can produce frequent sounds. Action-bar messages naturally replace each other client-side, but sound events still accumulate.

## Interaction with Unbreaking, Mending and other plugins

- Unbreaking/Paper may modify whether/how much durability damage is applied; the handler ignores `event.getDamage()`.
- Mending repairs occur through different mechanics/events and do not update any alert state because none is tracked.
- Item-repair plugins can change metadata without notifying this feature; the next damage event simply recalculates current percentage.
- Plugins at lower priorities can change the event before `HIGH`, but the handler still ignores the event's damage amount.
- Plugins at later priorities can invalidate the warning by cancelling/changing the event.
- Custom durability systems that do not fire `PlayerItemDamageEvent` are not covered.

## Threading and performance

`PlayerItemDamageEvent` is expected on the server thread. Processing is constant-time:

- item-meta inspection;
- one percentage calculation;
- localization/action-bar/sound only below threshold.

There are no tasks, asynchronous operations, maps or inventory scans.

## Persistence, database and messaging

DurabilityAlert has no:

- DataProvider/database integration;
- Redis/plugin messaging;
- cross-server state;
- API registration;
- PlaceholderAPI expansion;
- player settings;
- disk data beyond feature/localization config.

## Lifecycle

Initialization:

1. construct `DurabilityAlertHandler`;
2. directly read/cache `defaultvalue`;
3. register `DurabilityAlertListener`.

Disable has no explicit body. Lifecycle cleanup unregisters the listener. There is no state or task to clear.

## Developer source map

- Defaults/messages/lifecycle: `features/durabilityalert/DurabilityAlert.java`
- Calculation/output: `features/durabilityalert/internal/DurabilityAlertHandler.java`
- Event wiring: `features/durabilityalert/listener/DurabilityAlertListener.java`
- Metadata: `features/durabilityalert/meta/Meta.java`

## Operational verification

1. Test tools and armor with several maximum-durability values around the configured percentage boundary.
2. Verify exact-threshold inclusion.
3. Test event damage values greater than one and observe displayed-value inaccuracy.
4. Test Unbreaking, Mending and plugins that modify/cancel `PlayerItemDamageEvent` at lower/later priorities.
5. Verify custom item names/components appear correctly as `{item}`.
6. Trigger repeated damage below threshold and assess action-bar/sound spam.
7. Test `defaultvalue` at negative, 0, 10, 100 and >100 values.
8. Test out-of-range/custom damage metadata in a non-production environment.
9. Reload config and verify threshold changes require feature reconstruction.
10. Confirm custom durability mechanics that bypass the event are intentionally not covered.

## Troubleshooting

- **Warning appears one hit late:** threshold uses pre-event item damage rather than the final event damage.
- **Displayed durability is wrong:** the handler subtracts one and ignores `event.getDamage()`.
- **Warning says broken but item survives:** a later plugin cancelled/changed damage, Unbreaking affected the event, or event damage assumptions differ.
- **Alerts repeat constantly:** no cooldown/hysteresis exists.
- **Some custom items never alert:** they may not use Bukkit `Damageable`/material max durability or may bypass `PlayerItemDamageEvent`.
- **Config change has no effect:** threshold is cached in the handler; reload/re-enable.
- **Permission/slot filtering does not work:** neither exists in the implementation.
