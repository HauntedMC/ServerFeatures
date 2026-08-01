# SilkSpawners

> Paper · Feature ID `silkspawners` · disabled by default · command `/silkspawners`

SilkSpawners lets players with the relevant permissions pick up a naturally placed/configured mob spawner using a Silk Touch tool and later restore its entity type from Bukkit `BlockStateMeta`. It also provides a staff give command that creates typed spawner items directly.

The implementation is intentionally narrow: it owns normal player `BlockBreakEvent` and `BlockPlaceEvent` only. It does not handle explosions, pistons, WorldEdit, creative middle-click, dispensers, inventory conversion, spawner stacking or database persistence.

## Commands and permissions

| Syntax | Permission | Sender | Behaviour |
|---|---|---|---|
| `/silkspawners give <player> <mobtype> <amount>` | `serverfeatures.feature.silkspawners.give` | Player or console | Creates one typed spawner stack, adds it to an exact online player's inventory and drops leftovers in front of that player. |

The command has no alias in `CommandMeta`. The permission check is performed inside `execute`; the command meta itself does not declare a permission.

### Give command parsing

- The first argument must be literal `give`.
- Exactly four arguments are required.
- Target lookup uses `Bukkit.getPlayerExact`, so partial names and offline players are rejected.
- Mob type is upper-cased and passed to `EntityType.valueOf`.
- Amount must parse as an integer and be at least one.
- There is no configured maximum amount.
- There is no `EntityType#isAlive` validation during execution.
- The `allowed_spawner_types` whitelist is **not checked** by the give command.

Tab completion suggests:

1. `give`;
2. all online player names;
3. lower-case entity enum names for types where `EntityType#isAlive()` is true;
4. literal `<amount>`.

Suggestions do not filter online player names by the current prefix and are not permission-filtered in the method itself. Execution remains permission-protected.

### Give inventory behavior

The command creates a `Material.SPAWNER` item with the requested amount and calls `PlayerInventory#addItem`. Any leftovers are dropped naturally one block in front of the target, using the target's horizontal look direction.

The sender and recipient are always told the complete requested amount, regardless of how much fitted in inventory versus dropped. There is no audit/database record.

## Block permissions

| Permission | Behaviour |
|---|---|
| `serverfeatures.feature.silkspawners.mine` | Allows Silk Touch pickup of an allowed spawner type. Missing permission cancels the break and displays the hard-coded rank replacement `&6Legend`. |
| `serverfeatures.feature.silkspawners.place` | Allows placement of a typed, allowed spawner item. Missing permission cancels placement with the same rank message. |

No wildcard/bypass logic is implemented by the feature beyond the permission system's normal inheritance.

## Configuration

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `false` | Registers the handler, events and give command. |
| `allowed_spawner_types` | `ZOMBIE`, `SPIDER`, `CAVE_SPIDER`, `BLAZE`, `SILVERFISH`, `SKELETON`, `MAGMA_CUBE` | Entity enum names allowed for player break and placement. An empty list removes the restriction. |

The list is read once into the handler during feature initialization using `CastUtils.safeCastToList`.

Matching uses exact `List<String>#contains(type.name())`. Values should therefore use exact upper-case Bukkit enum names. Lower-case values such as `zombie`, namespaced IDs such as `minecraft:zombie`, whitespace and aliases do not match.

The list affects:

- successful Silk Touch pickup;
- placement of modern typed items;
- placement of recognized legacy items.

It does **not** affect `/silkspawners give` item creation. A staff member can create a type that players cannot subsequently place or mine under the current whitelist.

There are no settings for:

- requiring a particular tool material;
- drop chance;
- XP amount beyond the fixed zero on successful pickup;
- creative-mode behavior;
- item name/lore;
- legacy tags;
- world/region filters;
- amount limits;
- claim integration.

## Mining flow

`BlockBreakEvent` is handled at `HIGHEST` with `ignoreCancelled=true`.

Processing order:

1. Ignore non-spawner blocks.
2. Read the player's main-hand item.
3. If it does not contain `Enchantment.SILK_TOUCH`, return without modifying the event. Vanilla/Paper handles the ordinary break.
4. Require `serverfeatures.feature.silkspawners.mine`; otherwise cancel.
5. Cast the block state to `CreatureSpawner` and read `getSpawnedType()`.
6. If type is null, cancel silently.
7. Enforce the allowed-type list; otherwise cancel and report `{type}`.
8. Require `PlayerInventory#firstEmpty() != -1`; otherwise cancel and report no space.
9. Set event XP to zero.
10. Build one typed spawner item and add it to inventory.
11. Send the success message.

The feature does not cancel a successful break. The normal block break continues and removes the placed spawner. Vanilla spawners do not normally drop themselves, while this handler directly grants the typed item.

### Protection ordering

Because the listener ignores already-cancelled events and runs at `HIGHEST`, claim/region listeners at lower priorities can cancel first and prevent pickup. A later listener can still cancel after SilkSpawners has already added the item, because the item is granted inside the `HIGHEST` handler before the event reaches `MONITOR`.

That creates an integration boundary: another plugin cancelling the break later can leave the original block in place while the player retains the granted spawner. Protection plugins should cancel no later than `HIGHEST` with deterministic registration ordering, or the feature should eventually defer the grant until the event outcome is final.

### Inventory-space semantics

The mining path checks only for one completely empty inventory slot. It does not consider whether an existing compatible spawner stack has free capacity. Conversely, after finding an empty slot it ignores `addItem` leftovers, although a single generated item normally fits that slot.

A player with no empty slot but a partially filled identical spawner stack is rejected.

## Typed item format

`ItemUtils.createSpawnerItem(EntityType)` creates:

- material `SPAWNER`;
- a `BlockStateMeta` whose embedded `CreatureSpawner` has the requested `spawnedType`;
- yellow non-italic display name `<Pretty Entity Name> Spawner`;
- lore:
  - blank line;
  - `Right-click: Toggle Mob Spawning`;
  - `Mineable: Legend Rank+`;
- `HIDE_ATTRIBUTES` item flag.

Pretty names are generated from the enum by splitting `_` and title-casing each part.

The entity type is preserved through Bukkit's block-state item metadata, not a custom PersistentDataContainer key or database field. The lore text about toggling spawning describes integration/player behavior but SilkSpawners itself registers no right-click toggle listener; that belongs to the separate SpawnerToggle feature where enabled.

## Placement flow

`BlockPlaceEvent` is handled at `HIGHEST` with `ignoreCancelled=true`.

Processing order:

1. Ignore non-spawner placements.
2. Require `serverfeatures.feature.silkspawners.place`; otherwise cancel.
3. Read `event.getItemInHand()`.
4. Require `BlockStateMeta`; otherwise return without cancellation.
5. Require the embedded block state to be `CreatureSpawner`; otherwise return.
6. Read the embedded spawned type.
7. If type is absent, attempt narrow legacy extraction.
8. Enforce `allowed_spawner_types`.
9. Set the newly placed block state's spawned type and call `update()`.

A plain/untyped `SPAWNER` item with no compatible block-state metadata is not rejected after the permission check; the handler returns and leaves Paper's default placed state intact.

### Legacy compatibility

When modern `CreatureSpawner#getSpawnedType()` returns null, the feature serializes item meta through `ItemMeta#getAsString()` and searches this regular expression:

```text
ms_mob\s*:\s*"([^"]+)"
```

The captured value is passed directly to `EntityType.valueOf`. It must therefore be an exact enum name such as `ZOMBIE`.

When recognized, the handler creates a modern item for the type and copies only the generated spawner block data to the placed state before later calling `setSpawnedType(type)`. It does not replace the player's remaining legacy stack or migrate inventory metadata.

When the tag is absent, placement is cancelled and only `Unknown legacy spawner detected` is logged; the player gets no specific message. An invalid captured enum value can throw from `EntityType.valueOf` because that call is not caught.

### Placement update semantics

`CreatureSpawner#update()` is called without explicit `force` or physics flags. Other spawner properties stored in item block state—delay, ranges, counts and similar values—are not deliberately copied by the handler; its contract is the spawned entity type.

## Event coverage

Owned events:

| Event | Priority | Cancelled events | Purpose |
|---|---|---|---|
| `BlockBreakEvent` | `HIGHEST` | ignored | Silk Touch pickup. |
| `BlockPlaceEvent` | `HIGHEST` | ignored | Permission/type validation and entity restoration. |

Not covered:

- `BlockExplodeEvent` / `EntityExplodeEvent`;
- pistons or falling blocks;
- WorldEdit/FAWE direct edits;
- creative pick-block/copy-NBT;
- hopper/dispenser behavior;
- spawner type changes by eggs/plugins;
- inventory click restrictions;
- right-click spawning toggle;
- stacker plugins.

## Messages and variables

| Key | Variables / use |
|---|---|
| `silkspawners.no_space` | Mining with no empty inventory slot. |
| `silkspawners.success` | `{type}` enum name after pickup. |
| `silkspawners.not_allowed_type` | `{type}` on break/place whitelist denial. |
| `silkspawners.give_usage` | Invalid command shape. |
| `silkspawners.player_not_found` | `{player}` exact input. |
| `silkspawners.invalid_mobtype` | `{type}` upper-cased input. |
| `silkspawners.invalid_amount` | Invalid/non-positive amount. |
| `silkspawners.give_success` | `{player}`, `{type}`, `{amount}`. |
| `silkspawners.receive_success` | `{type}`, `{amount}`. |
| `general.no_permission_rank` | `{rank}` fixed to `&6Legend` for mine/place. |
| `general.no_permission` | Give command denial. |

## Persistence and integrations

SilkSpawners has no database, Redis, DataRegistry or plugin-messaging integration. Persistent type data lives inside each item stack and the placed tile entity.

It relies on:

- Bukkit/Paper `CreatureSpawner` and `BlockStateMeta` serialization;
- normal protection-plugin event cancellation;
- the separate SpawnerToggle feature if the generated lore's right-click promise is desired.

There is no explicit API shared with SpawnerToggle; compatibility is through the placed block/item behavior.

## Important implementation boundaries

- Allowed-type matching is exact and case-sensitive.
- Empty allowed list means all types.
- Give bypasses allowed types and `isAlive` validation.
- Mining requires any main-hand item carrying Silk Touch; it does not verify tool category.
- Without Silk Touch, this feature does nothing, including no permission/whitelist check.
- Successful pickup grants before later event priorities can cancel.
- Mining requires an empty slot rather than stack capacity.
- Successful mining sets XP to zero.
- Plain/untyped spawners can be placed by permitted players without type validation.
- Legacy support recognizes only a quoted `ms_mob` field in serialized metadata.
- Legacy enum conversion is not exception-guarded.
- Item text is hard-coded English and not localized.
- `/silkspawners give` has no maximum and no database audit.
- The feature does not clean up or migrate items on disable.

## Verification checklist

1. Break every allowed default type with and without Silk Touch and each mining permission state.
2. Test a disallowed type and an empty whitelist.
3. Test lower-case, namespaced and whitespace-padded whitelist entries to confirm exact matching.
4. Fill inventory completely, then test both an existing partial compatible stack and one empty slot.
5. Combine with each protection plugin, especially one cancelling at `HIGHEST` or later, and test for duplication.
6. Place a modern generated item and inspect the resulting `CreatureSpawner#getSpawnedType()`.
7. Place an untyped vanilla spawner item and document the server's default type.
8. Test recognized, missing and invalid `ms_mob` legacy metadata.
9. Use `/silkspawners give` for an allowed type, a disallowed-but-valid type, a nonliving enum and a very large amount.
10. Fill the target inventory and verify leftover drop position/amount.
11. Test explosions, pistons, WorldEdit and other spawner plugins separately; they are outside feature ownership.
12. Enable SpawnerToggle and confirm the generated lore's right-click behavior is actually supplied there.

## Source map

- Defaults/messages/lifecycle: `features/silkspawners/SilkSpawners.java`
- Break/place logic: `features/silkspawners/internal/SilkSpawnersHandler.java`
- Event priorities: `features/silkspawners/listener/SilkSpawnersListener.java`
- Give command: `features/silkspawners/command/SilkSpawnerCommand.java`
- Modern item format: `features/silkspawners/util/ItemUtils.java`
- Legacy parser: `features/silkspawners/util/LegacyUtils.java`
