# ItemEdit

> Paper · Feature name `ItemEdit` · feature package `features.itemedit` · disabled by default

ItemEdit allows permitted players to use colour codes, hex formats and a restricted subset of MiniMessage in an anvil rename field. It only intervenes when formatting syntax is detected; ordinary unformatted anvil renames remain vanilla. The feature can suppress formatted results for configured materials and reject exact blocked plain names.

It does not provide commands, lore editing, item serialization, arbitrary NBT/component editing, length limits, persistent settings, database, Redis, API or PlaceholderAPI integration.

## Commands and permission

No command is registered.

Formatting permission:

```text
serverfeatures.feature.itemedit.anvilcolors
```

The permission is checked only when the rename text contains a supported formatting pattern. A player without it can still perform ordinary vanilla unformatted renames.

When denied, the feature sends `general.no_permission_rank` with hard-coded `{rank}` value `&2Legend`, but it does **not** explicitly clear/cancel the anvil result. Because the handler returns after sending the message, Paper/vanilla may still present an ordinary/raw rename result depending on how formatting syntax is interpreted by the client/server. The permission controls ItemEdit's formatted-component rewrite, not necessarily taking the item out of the anvil entirely.

## Complete configuration reference

File: `plugins/ServerFeatures/features/ItemEdit/config.yml`.

| Key | Generated default | Actual implementation use |
|---|---|---|
| `enabled` | `false` | Registers listener and constructs handler. |
| `blockedWords` | `['kut','godverdomme']` | **Not read by the current handler.** This generated key is effectively unused. |
| `blockedNames` | not generated | The handler reads this key once through `CastUtils.safeCastToList`. Only exact plain-name matches are rejected. Unless operators manually add it, the effective list is empty. |
| `blockedAnvilItems` | `['CHEST','HOPPER']` | Bukkit material-name strings for formatted rename results that should be replaced with `AIR`. Read once at handler construction. Matching is exact/case-sensitive against `result.getType().name()`. |

The `blockedWords` versus `blockedNames` mismatch is important: the bundled profanity defaults do not currently affect renaming. Correcting the code/config mismatch should be handled explicitly so existing installations do not receive surprising policy changes.

There are no keys for maximum length, permitted format features, click/hover security, lore, allowed items, costs, sounds, worlds or messages.

## Event contract and ordering

```java
@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
public void onPrepareAnvil(PrepareAnvilEvent event)
```

`PrepareAnvilEvent` fires repeatedly while input items/rename text change. The feature receives the current preview result and can replace it.

Flow:

1. read `event.getView().getRenameText()`;
2. return for null/empty text;
3. inspect whether text contains any supported formatting syntax;
4. return when no formatting is detected;
5. check permission;
6. deserialize restricted visual formatting to an Adventure component;
7. force italic false on the component root;
8. serialize to plain text and trim for exact blocked-name validation;
9. return when plain name equals any configured blocked name;
10. obtain current result;
11. return when result is null;
12. when result material name is in blocked-anvil-items, replace result with `AIR`;
13. otherwise, when result has item meta, set only `ItemMeta#displayName` and place result back into event.

The listener does not handle `InventoryClickEvent`, result-taking, costs, inventory close, shift-click, number keys or drag. It modifies the preview only and relies on vanilla/Paper anvil transaction semantics.

`ignoreCancelled=true` is unusual because `PrepareAnvilEvent` cancellation capabilities depend on API version; already-cancelled compatible events are ignored.

Same/later-priority plugins may overwrite the result after ItemEdit. There is no final `HIGHEST`/`MONITOR` enforcement.

## Formatting detection

`FormatInspector.containsFormatting` checks these input formats:

- `LEGACY_AMPERSAND` (`&a`, decorations, etc.);
- `LEGACY_SECTION` (`§a`);
- `HEX_POUND` (`#RRGGBB` according to shared parser rules);
- `HEX_BUNGEE_AMP`;
- `HEX_BUNGEE_SECTION`;
- `HEX_MINI`;
- `MINIMESSAGE`.

If the inspector finds none, ItemEdit does nothing. A string that looks unusual but is not recognized remains vanilla.

Detection is broader than plain colour codes and depends on the shared formatter's syntax recognition. Operators should test literal text containing `<...>`, `#...` or ampersands to ensure it is not unintentionally treated as formatting.

## Allowed component features

The formatted name is deserialized with `MIXED_INPUT` and only:

- colours;
- text decorations;
- gradients;
- rainbow;
- reset.

Not enabled:

- click events;
- hover events;
- insertion;
- fonts;
- translatable/keybind/NBT selectors;
- URLs;
- PlaceholderAPI preprocessing.

This restriction prevents anvil text from creating interactive item names through the shared formatter.

After parsing, the root component is forced to `TextDecoration.ITALIC = false`. Child components with explicit italic decoration may follow Adventure decoration inheritance/overrides; test nested MiniMessage italics if complete suppression is required.

## Blocked-name validation

The parsed formatted component is serialized to plain text and trimmed. Each configured `blockedNames` value is compared with `equalsIgnoreCase`.

This means:

- exact whole-name only;
- no substring, token, regex, normalization, punctuation folding or Unicode confusable handling;
- config values are not trimmed/lowercased by the handler;
- formatting is removed before comparison;
- leading/trailing rendered spaces are ignored on the result side;
- an exact blocked match causes a silent return, not a cleared result or feedback.

Because return leaves the current result as generated before ItemEdit, a blocked formatted name may still appear as vanilla/raw rename output rather than being impossible to take. The feature does not call `event.setResult(AIR)` for blocked names. This should be tested and hardened if blocking must be authoritative.

The generated `blockedWords` list is currently disconnected from this logic.

## Blocked item materials

After successful formatting/validation, the current result type's enum name is checked against the loaded `blockedAnvilItems` string list.

Default generated values:

- `CHEST`
- `HOPPER`

When matched, result is replaced with a new `ItemStack(Material.AIR)`, making the formatted preview unavailable.

Limitations:

- comparison is case-sensitive; use uppercase Bukkit enum names;
- no material parsing/validation/warnings at load;
- applies only to formatted renames, because unformatted text returns earlier;
- list is cached until feature reconstruction;
- no tags/categories or custom-item/PDC checks;
- exact result material after other prior plugins is used.

## Metadata preservation

The feature modifies only:

```java
ItemMeta#displayName(coloredNameComponent)
```

on the current result stack/meta, then sets that meta back. It does not deliberately alter:

- lore;
- enchantments;
- attributes;
- custom model data/item components;
- PDC/NBT;
- damage;
- unbreakable;
- skull profiles;
- container contents;
- repair cost.

Because it starts from `event.getResult()` and its existing meta, unrelated properties should be preserved. Compatibility still depends on custom-item plugins not replacing result/meta afterward.

## Anvil costs and gameplay

ItemEdit does not modify:

- repair cost/maximum cost;
- level requirements;
- material consumption;
- result availability due to vanilla "Too Expensive" rules;
- item compatibility/combinations;
- result-click handling.

If `event.getResult()` is null because vanilla/anvil logic provides no result, ItemEdit returns.

## Messages

The feature defines no feature-specific localization keys. It uses shared:

- `general.no_permission_rank` with `{rank}` = literal `&2Legend`.

Blocked names/items receive no direct message.

## Threading and performance

`PrepareAnvilEvent` runs on the server thread and can fire frequently while typing. Every formatted update performs:

- formatting inspection;
- mixed-input component parse;
- plain serialization;
- linear blocked-name/material list checks;
- item-meta clone/set work.

Keep configured lists modest. No async work/tasks/state maps exist.

## Persistence, database and messaging

None. ItemEdit uses no DataProvider, database, Redis/proxy messaging, API, PAPI expansion or custom data file. Config lists are the only durable input.

## Lifecycle

Initialization currently registers `AnvilListener` **before** assigning `itemHandler = new ItemHandler(this)`. Normal event dispatch does not occur synchronously during listener registration, but the ordering creates a narrow theoretical risk if another component triggers the listener before handler assignment. Constructing the handler first would be safer.

Handler snapshots lists at initialization. Disable is empty; lifecycle cleanup unregisters listener.

## Developer source map

- Defaults/lifecycle: `features/itemedit/ItemEdit.java`
- Formatting/validation/result mutation: `features/itemedit/internal/ItemHandler.java`
- Event wiring: `features/itemedit/listener/AnvilListener.java`
- Shared formatter/inspector: API text formatting package
- Metadata: `features/itemedit/meta/Meta.java`

## Operational verification

1. Test ordinary unformatted renames with/without permission—ItemEdit should not intervene.
2. Test every supported colour-code/hex/MiniMessage format.
3. Test hostile click/hover/font/etc. tags and verify restricted features.
4. Test root/nested italics.
5. Add `blockedNames` manually and test exact/case/whitespace/punctuation/substring results.
6. Confirm generated `blockedWords` currently has no effect.
7. Test blocked materials with uppercase/lowercase names and formatted versus unformatted renames.
8. Verify blocked-name return and permission-denied return do not unintentionally leave a takeable raw result.
9. Test enchantments, lore, PDC, container contents, damage and custom-item metadata preservation.
10. Test anvil cost/too-expensive behaviour and other anvil plugins at earlier/later priorities.
11. Reload config and confirm list changes require feature reconstruction.

## Troubleshooting

- **Default blocked profanity is not blocked:** code reads `blockedNames`, while defaults write `blockedWords`.
- **Blocked name still yields a result:** exact match only returns without clearing the existing result; verify Paper preview and harden if necessary.
- **Player without permission can still rename:** ordinary unformatted names are intentionally vanilla; formatted denial does not explicitly clear result.
- **Blocked item still renames:** list values are exact uppercase material names and apply only when formatting is detected.
- **Interactive tags disappear:** click/hover and other unsafe features are intentionally not enabled.
- **Custom metadata disappears:** another anvil/custom-item plugin may replace result/meta after ItemEdit; inspect listener ordering.
- **Config changes do not apply:** lists are cached at handler construction.
