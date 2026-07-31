# JoinItems

> Paper · Feature name `JoinItems` · feature package `features.joinitems` · disabled by default

JoinItems places configured PDC-tagged utility items into fixed player-inventory slots after a short delay, optionally purges inventory content on join/leave, executes configured commands when a managed main-hand item is used, and enforces per-definition movement/drop/place restrictions.

Definitions and runtime behaviour are local to the Paper backend. There is no permission or world filter, custom-model/PDC configuration beyond the internal ID tag, player preference, persistence, database, Redis, API or PlaceholderAPI expansion.

## Commands and permissions

JoinItems registers no feature command and checks no permission. Every locally online player receives the configured items.

Commands attached to items execute through:

```java
player.performCommand(commandWithoutLeadingSlash)
```

They execute as the player and are subject to that command's normal permissions/policies. JoinItems itself does not grant temporary permissions or execute as console.

## Complete feature configuration

File: `plugins/ServerFeatures/features/JoinItems/config.yml`.

| Key | Default | Exact behaviour and edge cases |
|---|---:|---|
| `enabled` | `false` | Loads definitions, registers listeners, and initializes already-online players. |
| `include-all-items` | `false` | Controls purge scope. `false` removes only JoinItems PDC-tagged items. `true` clears all storage/hotbar while preserving armor and offhand. The name can be misleading: it does not control which definitions are included. |
| `remove-on-join` | `true` | Before giving configured items after the delay, call purge according to `include-all-items`. |
| `remove-on-leave` | `true` | During `PlayerQuitEvent`, purge according to `include-all-items`. |
| `join-delay` | `2` ticks | Delay before purge/give on join or feature enable. Cached at reload; getter clamps negative to zero. |

Global options are read once by `reloadFromConfig`. Changing them requires feature reconstruction or an explicit programmatic reload.

### Dangerous `include-all-items` semantics

When true, `purgeFor`:

1. snapshots armor contents;
2. snapshots offhand;
3. calls `PlayerInventory#clear()`;
4. restores armor and offhand.

This removes every storage/hotbar item, including unrelated survival items, menus and plugin items. It does not preserve cursor inventory or other open-container content. Use only on lobby-style backends where that ownership is intended.

When false, managed items are removed from all normal inventory slots and separately from offhand. Armor slots inside `PlayerInventory#getSize()` are implementation-dependent; the loop checks every reported inventory index.

## `local/joinitems.yml` definition reference

Path:

```text
plugins/ServerFeatures/local/joinitems.yml
```

Expected root:

```yaml
items:
  navigator:
    material: COMPASS
    slot: 0
    name: '<aqua><bold>Server navigator'
    lore:
      - '<gray>Right-click to open'
    command: server
    commands:
      - menu
    locked: true
    unmovable: true
    undroppable: true
```

Every direct child under `items` becomes one definition. Child order is preserved in a `LinkedHashMap` and determines give order. IDs are normalized lowercase with `Locale.ROOT`.

### Fields

| Field | Default | Behaviour |
|---|---:|---|
| child ID | required | Lowercase registry/PDC ID. Case-only duplicates overwrite earlier definitions/templates while loaded count still increments. |
| `material` | `STONE` | Parsed with `Material.matchMaterial`. Invalid material warns and falls back to stone. |
| `slot` | `0` | Raw player-inventory index. Invalid slots `<0` or `>= inventory.getSize()` are silently skipped during give. |
| `name` | empty | Mixed-input Adventure component with all default formatter features; italics forced false when item template is built. |
| `lore` | empty list | Each line parsed as mixed input/all defaults; italics forced false on each line. |
| `command` | none | Single command merged with `commands`. |
| `commands` | empty list | Ordered string list. Entries are trimmed, one leading slash removed, blanks discarded. |
| `locked` | `true` | Prevents `BlockPlaceEvent` only. It does not independently prevent consuming/using because all managed item interactions are cancelled regardless of this flag. |
| `unmovable` | `true` | Cancels supported inventory clicks and hand swaps involving this managed definition. |
| `undroppable` | `true` | Cancels `PlayerDropItemEvent`. |

No fields exist for amount, custom model data, enchantments, item flags, PDC/NBT, skull texture, damage, unbreakable, permission, world, gamemode, click type, cooldown, sound or console-command execution. Every template amount is one.

## Managed-item identity and template construction

Internal namespaced PDC key:

```text
<ServerFeatures namespace>:joinitems_item
```

Value: lowercase definition ID as `PersistentDataType.STRING`.

A stack is considered managed solely when it has item metadata and a non-blank string under that key. Material, name, lore, slot and amount are not validated.

Consequences:

- copied/renamed/modified managed items remain managed while PDC survives;
- any plugin/item carrying the same key is managed;
- old PDC IDs no longer present in current definitions are recognized by `isManaged` but `definitionOf` is empty, so some definition-specific protections/commands do not apply;
- purge with `include-all-items=false` removes even stale/unknown managed IDs;
- ordinary lookalike items without PDC are unaffected.

Templates are cached per ID and cloned for handover. Only display name, lore and internal PDC ID are set.

## Join/enable initialization ordering

`initializePlayer(player)` schedules a delayed feature task:

1. after `max(0, join-delay)` ticks, require `player.isOnline()`;
2. when `remove-on-join`, purge inventory;
3. call `giveAll`;
4. `giveAll` overwrites each valid configured slot with a clone;
5. call `player.updateInventory()`.

Feature initialization calls this for players already online after listener registration/config load. `PlayerJoinEvent` at `MONITOR` calls the same method.

There is no generation/session token. Multiple calls before their delays expire can run multiple purge/give cycles. Rapid feature reload plus join or duplicate initialization can overwrite slot contents repeatedly.

`giveAll` does not preserve or relocate an existing item in a configured target slot; it overwrites it. With `remove-on-join=false`, unrelated target-slot items can be silently replaced.

## Player lifecycle events

### Join

```java
@EventHandler(priority = EventPriority.MONITOR)
```

Schedules delayed initialization. `ignoreCancelled` is not relevant.

### Quit

```java
@EventHandler(priority = EventPriority.MONITOR)
```

When `remove-on-leave` is true, purge executes synchronously during quit. With `include-all-items=true`, the playerdata save following quit may persist an emptied storage/hotbar. Armor/offhand are restored before save.

The feature does not cancel pending delayed initialization explicitly, but callback checks online state.

There is no death/respawn/world-change listener. Managed items can drop through ordinary death inventory rules unless another system prevents it; `PlayerDropItemEvent` does not represent all death drops.

## Interaction and command execution

```java
@EventHandler(priority = EventPriority.HIGHEST)
public void onInteract(PlayerInteractEvent event)
```

Notably, `ignoreCancelled` is false (default).

Flow:

1. ignore `Action.PHYSICAL`;
2. require main hand to avoid duplicate offhand events;
3. read current main-hand stack directly;
4. resolve current definition by PDC ID;
5. perform every configured command in order;
6. cancel the interaction regardless of `locked` flag and regardless of whether command succeeded.

Implications:

- already-cancelled interactions still execute commands because handler does not test cancellation;
- all managed items with a current definition are non-usable/non-consumable/non-placeable through interact, even `locked=false`;
- command failures/permission denial do not stop later commands;
- no click-action distinction—left/right click air/block all execute except physical;
- main-hand item is evaluated, so event `getItem()` discrepancies do not matter;
- a command can change inventory/server state while handler still cancels original interaction afterward.

### Block placement

At `NORMAL`, `ignoreCancelled=true`, a managed item is cancelled only when its current definition has `locked=true`. In normal player use, `PlayerInteractEvent` at `HIGHEST` already cancels interaction for every current definition, so this is additional protection for placement paths/event ordering.

Unknown/stale managed IDs have no definition and are not cancelled by place handler.

## Movement/drop protections

### Drop

`PlayerDropItemEvent`, `NORMAL`, `ignoreCancelled=true`:

- resolve definition from dropped stack;
- cancel only when `undroppable=true`.

Unknown managed IDs can be dropped because `definitionOf` is empty.

### Swap hands

`PlayerSwapHandItemsEvent`, `NORMAL`, `ignoreCancelled=true`:

- when either side is managed, inspect each current definition;
- cancel when either managed side is `unmovable=true`.

Unknown managed IDs do not block swap.

### Inventory click

`InventoryClickEvent`, `NORMAL`, `ignoreCancelled=true`:

- inspect current item and cursor;
- when neither has JoinItems PDC, return;
- cancel when current/cursor definition is unmovable;
- for number-key clicks, also inspect selected hotbar item and cancel when unmovable.

Coverage limitations:

- no `InventoryDragEvent` listener;
- no creative-specific inventory event listener;
- no pickup (`EntityPickupItemEvent`) protection;
- no hopper/container movement protection;
- no swap-offhand key path beyond `PlayerSwapHandItemsEvent`;
- no explicit shift/double/drop-click type handling beyond current/cursor checks;
- unknown/stale managed IDs are recognized but do not have unmovable policy;
- managed item moved by another plugin/direct API is not prevented.

The `ClickType click` local is only specially used for `NUMBER_KEY`.

## Formatting and security

Name/lore use `ComponentFormatter.ALL_DEFAULTS`, unlike the restricted ItemEdit pipeline. Configuration is trusted server data, so click/hover/etc. can potentially be embedded in item components where the item API/client supports them. No PlaceholderAPI preprocessing is explicitly applied.

Commands are trusted configuration and can invoke any player-accessible command. There is no placeholder replacement such as `{player}` by JoinItems itself; third-party command parsing determines support.

## Reload semantics

`reloadFromConfig` clears definitions/templates and reloads global options/local file. No command calls it. Feature reconstruction applies changes.

Existing managed items retain old PDC IDs/templates until purge. After removing a definition:

- stale item remains managed for purge;
- it executes no commands;
- definition-specific movement/drop/locked checks no longer apply.

After changing a definition under the same ID, existing item is still associated with new definition policies/commands based on PDC, even though its material/name/lore may be old.

## Disable behaviour

`disable()` is empty. Framework cleanup removes listeners/tasks, but the feature does not purge managed items from online players on disable. Existing PDC-tagged items remain as ordinary items and can become movable/usable/dropable because protections stop.

Re-enabling initializes online players after delay, optionally purges and overwrites configured slots.

## Persistence, database and messaging

No database or Redis. Items persist naturally in player inventories/playerdata if not purged. The feature's only ownership marker is PDC stored in item metadata.

No API/PAPI expansion is registered.

## Developer source map

- Defaults/lifecycle: `features/joinitems/JoinItems.java`
- Definition/config/template/purge: `features/joinitems/internal/JoinItemsHandler.java`
- Immutable model/formatting: `features/joinitems/model/JoinItemDefinition.java`
- Events/protections/commands: `features/joinitems/listener/JoinItemsListener.java`
- Model tests: `src/test/.../features/joinitems/model/JoinItemDefinitionTest.java`
- Metadata: `features/joinitems/meta/Meta.java`

## Operational verification

1. Test all feature global options and delayed join/online enable.
2. Verify slot indexes and overwrite/loss behaviour with unrelated items.
3. Test include-all-items purge with storage/hotbar/armor/offhand.
4. Test valid/invalid materials, duplicate case IDs, invalid slots and all formatting.
5. Test command order, slash stripping, permission failures and every click action.
6. Test already-cancelled interactions—commands currently still run.
7. Test locked=false item placement/use; interaction cancellation still applies.
8. Test unmovable/undroppable combinations across clicks, number keys, cursor, swap, drag, creative, death and plugin movement.
9. Remove/change definitions while old PDC items exist.
10. Disable feature with managed items and verify protection cessation.
11. Test quit save with remove-on-leave/include-all-items.
12. Check conflicts with every lobby/hotbar plugin claiming the same slots.

## Troubleshooting

- **Unrelated item vanished on join:** configured slot is overwritten; include-all-items may also purge all storage/hotbar.
- **`include-all-items` gives all definitions expectation:** key controls purge scope, not definition inclusion.
- **Item commands run despite another plugin cancelling interaction:** listener processes cancelled events at `HIGHEST`.
- **`locked=false` item still cannot be used:** interaction handler cancels every current managed definition; locked only gates block-place listener.
- **Managed item can be dragged/moved unusually:** no drag/creative/pickup/container listener covers every path.
- **Old item loses protections:** stale PDC ID without current definition has no policy, though purge still recognizes it.
- **Items remain after disable:** no disable purge exists.
- **Config changes do not apply to item appearance:** reload/re-enable and purge/regive; existing stacks are not rewritten automatically.
