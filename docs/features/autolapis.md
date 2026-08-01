# AutoLapis

> Paper · Feature name `AutoLapis` · feature package `features.autolapis` · disabled by default

AutoLapis gives permitted players a temporary, plugin-marked lapis stack in the secondary slot of a vanilla enchanting inventory. The stack is refilled after successful enchantments and removed when the inventory closes. It is a local inventory convenience feature; it does not grant experience, alter enchant offers, or persist virtual lapis.

## Behaviour at a glance

- Only real `Player` viewers with the use permission are eligible.
- The feature inserts a `LAPIS_LAZULI` stack into the enchanting inventory's secondary slot when the GUI opens.
- The stack is identified by a namespaced persistent-data marker, not by display name or amount.
- Clicking or dragging the marked stack is cancelled.
- After an enchant consumes lapis, the stack is restored on the next server tick.
- The marked stack is removed at inventory close so it cannot be returned to the player or dropped.
- There are no commands, messages, database records, Redis contracts, APIs, or PlaceholderAPI placeholders.

## Permission

`serverfeatures.feature.autolapis.use`

The permission is checked every time the feature handles an enchanting inventory event. A player who loses the permission while the inventory is open will no longer receive refill/protection/close cleanup through permission-gated handlers. Administrators should avoid removing this permission mid-session; closing the enchanting GUI before changing ranks is safest.

Console, NPCs, and other `HumanEntity` implementations are not eligible because `eligible()` requires `viewer instanceof Player`.

## Complete configuration reference

File: `plugins/ServerFeatures/features/AutoLapis/config.yml`.

| Key | Default | Meaning and edge cases |
|---|---:|---|
| `enabled` | `false` | Enables listener registration. |
| `stack_size` | `3` | Amount of marked lapis inserted and the target amount used when topping up after an enchant. The handler reads it once during initialization. No explicit clamp is applied; Bukkit's `ItemStack` rules and material max-stack behaviour therefore define the practical valid range. |

The handler's fallback when reading `stack_size` is `1`, while the generated default is `3`. A malformed/missing runtime node can therefore fall back to one even though a newly generated file contains three.

Recommended values are `1..64`. Values at or below zero or far above the normal maximum are unsupported configuration and should not be relied upon.

## Marker item contract

The virtual stack is a normal Bukkit `ItemStack` with:

- material: `Material.LAPIS_LAZULI`;
- amount: configured `stack_size`;
- display name: `Lapis (Rank Perk)` in aqua, non-italic;
- persistent-data key: `<ServerFeatures plugin namespace>:autolapis_marker`;
- persistent-data type/value: `BYTE = 1`.

Detection requires all of the following:

1. non-null stack;
2. material is lapis lazuli;
3. item has metadata;
4. the persistent-data container contains the marker key as `PersistentDataType.BYTE`.

The display name and exact byte value are not used for detection. A separately created lapis item carrying the same PDC key/type would be treated as AutoLapis-owned.

## Enchanting slot ownership

`ensureMarker(EnchantingInventory)` inspects `getSecondary()`:

- when the secondary slot does not contain the marker, it replaces the slot with a fresh marker stack;
- when the marker exists but its amount is lower than `stack_size`, it tops the amount back up;
- when the marker exists at or above the target amount, it leaves it unchanged.

This means an eligible player opening an enchanting inventory that already contains ordinary lapis in the secondary slot will have that slot replaced by the virtual marker. The current implementation does not transfer or refund the displaced ordinary lapis. Operators should therefore use this feature only where eligible players are expected to rely on virtual lapis and should test custom enchanting workflows carefully.

`clearMarker()` removes only a recognized marker item. Ordinary lapis is not removed on close.

## Event coverage and ordering

### `InventoryOpenEvent`

```java
@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
```

For an eligible viewer and an `EnchantingInventory`, the marker is inserted or topped up before the open completes. If another plugin cancelled the open at a lower priority, this feature ignores it.

Because this runs at `HIGHEST`, another same-priority listener has unspecified relative ordering. A `MONITOR` listener sees the resulting secondary slot.

### `EnchantItemEvent`

```java
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
```

After an uncancelled enchant, the feature schedules a one-time lifecycle task for the next tick. The delay allows vanilla/Paper to subtract lapis first; the follow-up then restores the configured amount.

The task captures the enchanting inventory instance. It is lifecycle-managed, so feature shutdown prevents unmanaged callbacks from surviving. Plugins that replace/close the inventory during the same event should be tested because the next-tick callback still calls `ensureMarker` on the captured inventory object.

### `InventoryClickEvent`

```java
@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
```

The handler only acts when the top inventory is enchanting and the clicker is eligible. It cancels the click when either:

- `event.getCurrentItem()` is the marker; or
- `event.getCursor()` is the marker.

After cancellation it calls `ensureMarker()` again. This covers normal pickup/place attempts and attempts where a marker is already on the cursor.

The implementation does not inspect click type, hotbar button, raw slot, shift-click destination, or collect-to-cursor separately. Protection depends on the current item/cursor marker check plus the invariant that the marker remains in the top secondary slot.

### `InventoryDragEvent`

```java
@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
```

When the old cursor is the marker, the drag is cancelled and the secondary slot is repaired. Drags using an ordinary item are not altered, even when raw slots include the enchanting inventory.

### `InventoryCloseEvent`

```java
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
```

For an eligible player closing an enchanting inventory, the secondary marker is removed. `InventoryCloseEvent` is generally not cancellable, but the annotation is consistent with the listener's event policy.

Close cleanup is permission-gated. If the player no longer has `serverfeatures.feature.autolapis.use` by the time close fires, this handler returns without clearing the marker. This is an implementation detail worth considering when rank/permission changes can happen while GUIs are open.

## Transaction and ordering model

The feature relies on the vanilla enchanting transaction for experience and enchant validation. It does not cancel or modify `EnchantItemEvent`, enchantment offers, levels, or the enchanted item.

Typical sequence:

1. eligible player opens enchanting table;
2. `HIGHEST` open handler inserts marker;
3. vanilla/Paper evaluates offers using the marker stack;
4. player enchants;
5. vanilla consumes lapis during the transaction;
6. `MONITOR` enchant handler schedules a next-tick refill;
7. refill restores marker amount;
8. close handler removes marker before inventory return/drop handling completes.

There is no explicit session object or per-inventory registry. Ownership is encoded entirely in the item PDC marker.

## Compatibility considerations

### Custom enchant plugins

Plugins that replace the top inventory with a custom type, move the lapis slot, use virtual inventories, cancel/reenact `EnchantItemEvent`, or copy item metadata may not follow these assumptions. AutoLapis only recognizes Bukkit `EnchantingInventory`.

### Inventory-management plugins

A plugin that moves the marker outside normal click/drag events could leak it into another inventory. The PDC marker makes such an item recognizable if returned to an enchanting slot, but this feature has no global item-removal listener.

### Multiple viewers

Vanilla enchanting inventories are normally single-viewer interfaces. The implementation does not keep per-viewer marker state. If a custom plugin exposes one enchanting inventory to multiple viewers with different permissions, one viewer's open/close can alter the shared secondary slot.

## Persistence, database and messaging

AutoLapis has no persistence. It does not register DataProvider, write a player setting, publish/subscribe on Redis, expose an API, or register PlaceholderAPI.

The marker is intended to exist only inside a currently open local enchanting inventory. A server restart, feature reload, or abnormal plugin interruption does not perform a world/inventory scan for leaked markers.

## Lifecycle

Initialization:

1. create `AutoLapisHandler`;
2. read and retain `stack_size`;
3. create the plugin namespaced marker key;
4. register `AutoLapisListener`.

Disable has no explicit cleanup body. The lifecycle manager unregisters the listener and cancels feature-owned next-tick tasks. Existing open enchanting inventories are not iterated and cleaned during disable; disable/reload should therefore be performed when no eligible player has an enchanting GUI open, or a dedicated cleanup pass should be added in future.

## Developer source map

- Feature defaults/lifecycle: `features/autolapis/AutoLapis.java`
- Marker creation and slot operations: `features/autolapis/internal/AutoLapisHandler.java`
- Event handling: `features/autolapis/listener/AutoLapisListener.java`
- Metadata: `features/autolapis/meta/Meta.java`

## Operational verification

1. Enable the feature and grant the use permission.
2. Open a vanilla enchanting table and confirm the secondary slot contains the named marked stack at the configured amount.
3. Enchant at every available cost and confirm the stack is restored on the next tick.
4. Attempt normal click, shift-click, number-key, double-click/collect, cursor placement and drag interactions involving the marker.
5. Close the inventory and verify no marker enters the player inventory or drops.
6. Open with ordinary lapis already in the secondary slot and verify/accept the current replacement behaviour.
7. Test a player without permission and confirm the feature does not alter their inventory.
8. Revoke permission while the GUI is open and observe the documented cleanup edge case.
9. Disable/reload while an enchanting inventory is open and verify whether an item remains; schedule operational reloads accordingly.
10. Test alongside every custom-enchant or custom-inventory plugin used on the server.

## Troubleshooting

- **No lapis appears:** verify the top inventory is a Bukkit `EnchantingInventory`, the feature is enabled, and the player has `serverfeatures.feature.autolapis.use`.
- **Only one lapis appears despite default three:** the config node may be missing or malformed; the runtime read fallback is `1`.
- **Real lapis disappears when opening:** current behaviour replaces any non-marker secondary item for eligible players. Do not preload that slot with real lapis.
- **Marker can be moved with an unusual action:** record the exact Bukkit event/click type; current protection directly checks current item, cursor, and old drag cursor rather than every routing mode.
- **Marker remains after close:** check whether permission was removed before close or the feature/listener was disabled while the GUI remained open.
- **Marker leaks after another plugin manipulates inventories:** the feature has no global scavenger. Remove leaked PDC-marked items administratively and address the incompatible inventory path.
