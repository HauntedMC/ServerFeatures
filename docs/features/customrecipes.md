# CustomRecipes

> Paper · Feature name `CustomRecipes` · feature package `features.customrecipes` · disabled by default

CustomRecipes loads an ordered YAML list of Bukkit recipes, registers/replaces them at feature startup, can intentionally remove existing recipes by key, and exposes commands to list or temporarily toggle definitions held by the current feature instance.

It supports shaped, shapeless, furnace, blasting, smoking, campfire, stonecutting, modern smithing-transform and `DISABLE` entries. Runtime enable/disable state is in memory only; there is no reload command, persistence, database, Redis, permission-based recipe discovery, custom item metadata parser or PAPI expansion.

## Commands and permissions

Root: `/customrecipes`, no aliases.

| Syntax | Permission | Sender | Behaviour |
|---|---|---|---|
| `/customrecipes list` | `serverfeatures.feature.customrecipes.command.list` | Player or console | Lists repository entries currently marked active. |
| `/customrecipes disable <namespace:key>` | `serverfeatures.feature.customrecipes.command.disable` | Player or console | Temporarily removes an active custom recipe, or temporarily restores a recipe defined with type `DISABLE`. |
| `/customrecipes enable <namespace:key>` | `serverfeatures.feature.customrecipes.command.enable` | Player or console | Re-registers a temporarily disabled custom recipe, or re-removes a temporarily restored `DISABLE` recipe. |

Missing/unknown subcommand sends shared `general.usage`, not a feature-specific root usage.

Tab completion:

- first argument always suggests `list`, `disable`, `enable` without permission filtering;
- `disable` suggests active repository keys;
- `enable` suggests keys marked disabled in memory;
- key order follows hash-map/collection iteration and is not stable.

Key parsing for command operations uses `NamespacedKey.fromString`. Bare key handling follows Bukkit's parser rather than automatically applying the ServerFeatures namespace; operators should use the exact `namespace:key` shown by `list`.

## Feature configuration

File: `plugins/ServerFeatures/features/CustomRecipes/config.yml`.

| Key | Default | Meaning |
|---|---:|---|
| `enabled` | `false` | Loads/registers recipes and command. |

All recipe definitions live in `local/recipes.yml`; there are no conflict/reload/discovery config settings in the feature file.

## Recipe definition file

Path:

```text
plugins/ServerFeatures/local/recipes.yml
```

Root must be a non-empty list:

```yaml
recipes:
  - key: custom_diamond_sword
    type: shaped
    output: "DIAMOND_SWORD,1"
    shape:
      - " A "
      - " A "
      - " B "
    ingredients:
      A: IRON_INGOT
      B: STICK
```

A missing/empty list logs a severe message and loads no definitions. Non-map entries are warned and skipped. One invalid entry does not intentionally stop later entries, but uncaught parser/Bukkit constructor exceptions can abort loading.

The bundled defaults demonstrate all implemented types except `DISABLE` and explicitly configured smithing `template`.

## Common definition fields

| Field | Default | Behaviour |
|---|---|---|
| `key` | `custom_recipe_<zero-based-list-index>` | Lowercased with default JVM locale. With `namespace:path`, constructs `new NamespacedKey(namespace,path)`; without namespace uses plugin namespace. Invalid namespace/path may throw because factory does not catch key-construction errors. |
| `type` | `shaped` | Case-insensitive enum: `SHAPED`, `SHAPELESS`, `FURNACE`, `BLASTING`, `SMOKING`, `CAMPFIRE`, `STONECUTTING`, `SMITHING`, `DISABLE`. Invalid types are warned/skipped. |
| `output` | required by most types | String `MATERIAL[,amount]`. Used by shaped/shapeless/cooking/stonecutting. Smithing uses `result`; disable uses no result. |

### Item string parser

`ParseUtils.parseItemStack`:

1. split the string on every comma;
2. parse first segment as Bukkit `Material.valueOf(uppercase)`;
3. default amount `1`;
4. parse second segment as integer when present; invalid amount silently falls back to `1`;
5. ignore segments after the second;
6. create a plain `ItemStack(material, amount)`.

No display name, lore, enchantment, PDC/NBT, custom-model data, damage, potion data, item components or exact metadata is supported. Amount is not explicitly clamped; Bukkit validates/represents the supplied value.

## Namespaced-key ownership and conflict policy

For every non-`DISABLE` entry during load:

1. if Bukkit already has a recipe under the key, attempt to remove it;
2. log success/failure of removal;
3. call `Bukkit.addRecipe(data.getRecipe())`;
4. log `Registered recipe` regardless of `addRecipe`'s boolean result;
5. register `RecipeData` in the in-memory repository.

Consequences:

- The feature may replace vanilla or another plugin's recipe under the same key.
- It does **not** retain the replaced prior recipe for ordinary custom types, so disabling ServerFeatures removes the custom recipe but cannot restore the overwritten one.
- `Bukkit.addRecipe` failure is not checked; repository/list output can claim an active definition even when registry insertion failed.
- Duplicate keys inside `recipes.yml` are processed sequentially; later repository registration overwrites earlier `RecipeData`, while Bukkit replacement attempts happen for each.

Use a unique plugin-owned namespace/path unless intentionally targeting an existing key with `DISABLE`.

## Recipe types

### `SHAPED`

Required:

```yaml
type: shaped
output: "MATERIAL,amount"
shape:
  - "ABC"
  - " D "
ingredients:
  A: MATERIAL
```

- `shape` may be a YAML list or any other object converted to string and split by commas.
- Missing `shape` causes a null dereference (`shapeObj.toString`) rather than a controlled warning.
- Shape is passed directly to `ShapedRecipe#shape`; Bukkit validates row count/width/symbol consistency and may throw.
- `ingredients` must be a map or the entry is skipped.
- Ingredient keys are trimmed and must be exactly one character; invalid keys are warned and skipped.
- Values are plain materials and use `RecipeChoice.MaterialChoice`.
- Unknown materials are warned and skipped; the recipe may still be returned with undefined shape symbols, which Bukkit can reject or make unusable.
- No exact item choices/tags/groups/categories are supported.

### `SHAPELESS`

```yaml
type: shapeless
output: "MATERIAL,amount"
ingredients:
  - MATERIAL
  - MATERIAL
```

`ingredients` must be a list. Every entry adds one material ingredient. Duplicate list entries require duplicate quantities. Unknown materials are warned/skipped; a recipe with zero valid ingredients can still reach registration and rely on Bukkit validation.

### `FURNACE`

```yaml
type: furnace
input: MATERIAL
output: "MATERIAL,amount"
experience: 0.7
cooking-time: 200
```

- input required, plain material;
- experience default `0.0`;
- cooking time default `200` ticks;
- parsing uses direct `Float.parseFloat`/`Integer.parseInt`; invalid supplied numbers throw and can abort loading;
- no recipe group/category or input tag/exact choice.

### `BLASTING`

Same fields as furnace; default cooking time `100` ticks.

### `SMOKING`

Same fields; default cooking time `100` ticks.

### `CAMPFIRE`

Same fields; default cooking time `100` ticks.

### `STONECUTTING`

```yaml
type: stonecutting
input: MATERIAL
output: "MATERIAL,amount"
```

Plain material input, no group/category/exact choice.

### `SMITHING`

Modern `SmithingTransformRecipe`:

```yaml
type: smithing
base: DIAMOND_SWORD
addition: NETHERITE_INGOT
template: NETHERITE_UPGRADE_SMITHING_TEMPLATE
result: "NETHERITE_SWORD,1"
```

- requires `base`, `addition`, `result`;
- all are plain materials/item string;
- `template` defaults to `NETHERITE_UPGRADE_SMITHING_TEMPLATE` when absent;
- invalid configured template warns and also falls back;
- base/addition/template are `MaterialChoice`;
- uses `result`, not common `output`;
- this is transform smithing only, not trim recipes.

### `DISABLE`

```yaml
type: disable
key: minecraft:some_recipe
```

Factory snapshots `Bukkit.getRecipe(key)` into `RecipeData`. During load, service looks up the key again and attempts removal:

- found+removed: stores the original recipe object for later restoration;
- not found/removal failure: warns, but still registers the repository entry;
- no `output` or other fields required.

This is the only type designed to restore a previously existing recipe on feature disable.

## Runtime repository and toggling semantics

`RecipeRepository` stores:

- `HashMap<NamespacedKey, RecipeData> registeredRecipes`;
- `HashSet<NamespacedKey> disabledKeys`.

No persistence is written back to YAML. Reload/restart reconstructs initial configured state and forgets command toggles.

### Disabling an ordinary custom recipe

- requires repository entry and not already disabled;
- requires recipe currently present in Bukkit registry;
- removes recipe;
- marks key disabled only after successful removal.

If another plugin already removed/replaced it, command fails and does not mark disabled.

### Enabling an ordinary custom recipe

- requires entry marked disabled;
- calls `Bukkit.addRecipe(data.getRecipe())` but ignores boolean result;
- always marks enabled and returns true afterward.

It can report success even when a key conflict prevented insertion.

### Disabling a `DISABLE` entry

This naming is counterintuitive. A configured `DISABLE` entry starts **active**, meaning the target recipe is removed. Running `/customrecipes disable <key>` temporarily disables the disabling rule by re-adding the captured original recipe, then marks the entry disabled.

Restoration succeeds only when captured `data.getRecipe()` is non-null and Bukkit accepts it.

### Enabling a `DISABLE` entry

Re-applies the disabling rule by removing the target recipe when it currently exists, then marks the entry active. Removal's boolean return is ignored in this path after existence check.

## List output and messages

`list` always sends `customrecipes.active_list_title`, then either no-active or one entry per active repository value.

| Key | Variables |
|---|---|
| `customrecipes.disable_usage` | none |
| `customrecipes.enable_usage` | none |
| `customrecipes.invalid_key` | `{key}` input |
| `customrecipes.disabled` | `{key}` normalized key |
| `customrecipes.disable_fail` | `{key}` |
| `customrecipes.enabled` | `{key}` |
| `customrecipes.enable_fail` | `{key}` |
| `customrecipes.active_list_title` | none |
| `customrecipes.no_active` | none |
| `customrecipes.list_entry` | `{key}`, `{type}` |

`list` reports repository state, not an authoritative fresh check of Bukkit registry state.

## Startup and shutdown ordering

Initialization:

1. create `RecipeService`/empty repository;
2. open/load `local/recipes.yml`;
3. process definitions in list order and mutate Bukkit registry;
4. register `/customrecipes`.

Disable iterates repository entries:

- skips entries currently marked disabled;
- active `DISABLE`: re-add captured original when non-null;
- active ordinary custom: remove current registry recipe when present;
- clears repository/disabled set.

Important edge cases:

- An ordinary custom recipe temporarily disabled via command is skipped during shutdown (already absent).
- A `DISABLE` entry temporarily disabled via command is skipped, leaving the original restored.
- Replaced third-party/vanilla recipes for ordinary custom types are not restored.
- `addRecipe` restoration result is not checked during shutdown for `DISABLE`.

## Reload behaviour

`RecipeConfigHandler` contains `reload()`, but no command/service method uses it. `/customrecipes` has no reload subcommand. Apply file changes through feature disable/re-enable.

During re-enable, unregister must complete before load to avoid stale key collisions. External plugins that rebuild recipes after ServerFeatures may override these entries.

## Threading and events

All registry mutations and command operations execute synchronously on the server thread. No listeners or recurring tasks are registered. Bukkit recipe discovery/crafting events are not intercepted; normal Bukkit/Paper recipe registry semantics apply.

The feature does not auto-discover recipes for players with `Player#discoverRecipe`, so client recipe-book visibility follows Bukkit/vanilla discovery rules.

## Persistence, database and messaging

CustomRecipes uses no DataProvider/database, Redis, proxy messaging, PlaceholderAPI or API service. The durable configuration is YAML only; command toggles are runtime-local.

## Security and validation considerations

Write access to `local/recipes.yml` grants ability to:

- remove arbitrary known recipe keys;
- replace recipes under arbitrary namespaces;
- create high-value outputs from cheap materials;
- set unusual amounts/cook times/experience;
- create invalid definitions that may interrupt startup.

Validate changes in a test server. The parser has inconsistent error handling: material/type errors generally warn, while malformed numeric/shape/key data can throw.

## Developer source map

- Feature lifecycle/messages: `features/customrecipes/CustomRecipes.java`
- YAML view: `features/customrecipes/config/RecipeConfigHandler.java`
- Factory/type dispatch: `features/customrecipes/internal/recipe/RecipeFactory.java`
- Per-type implementations: `features/customrecipes/internal/recipe/impl/`
- Item/numeric parser: `features/customrecipes/internal/util/ParseUtils.java`
- Registry mutation/toggle service: `features/customrecipes/internal/RecipeService.java`
- Runtime repository: `features/customrecipes/internal/RecipeRepository.java`
- Command: `features/customrecipes/command/CustomRecipesCommand.java`
- Bundled example: `src/main/resources/local/recipes.yml`
- Tests: `src/test/.../features/customrecipes/`

## Operational verification

1. Test every type with valid and invalid materials/amounts/numbers.
2. Verify namespace/default key generation and duplicate-priority/list indices.
3. Test malformed/missing shaped `shape`, ingredient mappings and Bukkit validation.
4. Test recipe conflicts with vanilla/other plugins and confirm overwritten ordinary recipes are not restored.
5. Test `DISABLE` load, command inversion semantics, and shutdown restoration.
6. Test list/enable/disable permissions and exact namespaced keys.
7. Force `Bukkit.addRecipe` conflicts and observe possible false success/repository mismatch.
8. Reload after runtime toggles and verify YAML initial state returns.
9. Check recipe-book discovery separately.
10. Verify feature disable removes only currently active custom entries and handles externally changed registry state.

## Troubleshooting

- **Docs/config mention reload but command lacks it:** current command implements only list/disable/enable.
- **Recipe listed but unavailable:** `addRecipe` result is ignored; inspect key conflicts/Bukkit registry.
- **Existing plugin recipe vanished:** custom load removes any existing recipe under its key and does not preserve ordinary replacements.
- **Disabled recipe returns when running `disable`:** for type `DISABLE`, command disable means disable the disabling rule, so restoration is intentional.
- **Recipe does not return after feature shutdown:** only active `DISABLE` definitions preserve/restore prior recipes.
- **Startup aborts on malformed YAML:** some shape/key/numeric parsing errors are uncaught; validate file types and values.
- **Custom NBT item cannot be configured:** parser supports material and amount only.
- **Changes do not apply:** there is no active reload command; re-enable feature.
