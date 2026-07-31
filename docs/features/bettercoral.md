# BetterCoral

> Paper · Feature name `BetterCoral` · feature package `features.bettercoral` · disabled by default

BetterCoral has two independent behaviours:

1. it prevents all vanilla `BlockFadeEvent` transitions for the five live coral families, including blocks, plants, floor fans and wall fans;
2. it optionally registers furnace recipes that convert each live coral variant into its matching dead variant.

There are no world filters, permissions, commands, player settings, database records, Redis messages, APIs or PlaceholderAPI placeholders.

## Complete configuration reference

File: `plugins/ServerFeatures/features/BetterCoral/config.yml`.

| Key | Default | Meaning and edge cases |
|---|---:|---|
| `enabled` | `false` | Enables the fade listener and, when configured, recipe registration. |
| `furnace.enabled` | `true` | Registers twenty live-to-dead coral furnace recipes during feature initialization. Read once; changing it requires reload/re-enable. |
| `furnace.cook_time_ticks` | `2` | Cook time supplied to each `FurnaceRecipe`. The recipe constructor fallback used by the implementation is `20` if the node cannot be read as an integer, while the generated default is `2`. No explicit positive clamp is applied. |
| `furnace.experience` | `0.0` | Experience supplied to each furnace recipe. Read as `Float`; the generated YAML value may be represented as another numeric type depending on the config backend, so verify the runtime config service converts it as expected. Fallback is `0.0F`. |

The feature config does not expose lists of materials, eligible worlds, event priorities, recipe keys, or block-transition types. All material mappings are hard-coded.

## Coral-preservation event contract

The listener handles:

```java
@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onCoralFade(BlockFadeEvent event)
```

At `HIGHEST`, after lower-priority plugins have had an opportunity to act, it checks the current block material. When the material is one of the hard-coded live coral variants, the event is cancelled.

Events already cancelled by another plugin are ignored. The listener does not inspect `event.getNewState()`; any fade event emitted for a live coral source block is cancelled regardless of the proposed target state or cause.

### Protected materials

The protected set contains all five colours/families in all four live forms:

| Family | Block | Plant | Floor fan | Wall fan |
|---|---|---|---|---|
| Tube | `TUBE_CORAL_BLOCK` | `TUBE_CORAL` | `TUBE_CORAL_FAN` | `TUBE_CORAL_WALL_FAN` |
| Brain | `BRAIN_CORAL_BLOCK` | `BRAIN_CORAL` | `BRAIN_CORAL_FAN` | `BRAIN_CORAL_WALL_FAN` |
| Bubble | `BUBBLE_CORAL_BLOCK` | `BUBBLE_CORAL` | `BUBBLE_CORAL_FAN` | `BUBBLE_CORAL_WALL_FAN` |
| Fire | `FIRE_CORAL_BLOCK` | `FIRE_CORAL` | `FIRE_CORAL_FAN` | `FIRE_CORAL_WALL_FAN` |
| Horn | `HORN_CORAL_BLOCK` | `HORN_CORAL` | `HORN_CORAL_FAN` | `HORN_CORAL_WALL_FAN` |

Dead coral is not included and unrelated `BlockFadeEvent` sources such as ice/snow remain untouched.

### What is not covered

The feature does not listen to generic physics, block-form, block-spread, structure growth, piston, explosion, WorldEdit or direct `Block#setType` changes. A plugin that directly replaces coral without firing/allowing `BlockFadeEvent` bypasses preservation.

## Furnace recipe contract

When `furnace.enabled` is true, `CoralRecipes` creates one `FurnaceRecipe` for every live material above, mapping to the corresponding dead material.

Examples:

- `TUBE_CORAL_BLOCK` → `DEAD_TUBE_CORAL_BLOCK`
- `BRAIN_CORAL` → `DEAD_BRAIN_CORAL`
- `FIRE_CORAL_FAN` → `DEAD_FIRE_CORAL_FAN`
- `HORN_CORAL_WALL_FAN` → `DEAD_HORN_CORAL_WALL_FAN`

All twenty mappings are one-to-one and preserve family/form.

### Recipe keys

Each recipe is registered under the ServerFeatures plugin namespace with path:

```text
coral_burn_<lowercase-live-material-name>
```

For example:

```text
serverfeatures:coral_burn_tube_coral_block
```

The exact namespace is derived from `new NamespacedKey(feature.getPlugin(), ...)`, so it follows the plugin's Bukkit namespace.

### Recipe properties

- input choice: exact `RecipeChoice.MaterialChoice` for one live coral material;
- result: one matching dead coral item;
- experience: shared configured `furnace.experience`;
- cook time: shared configured `furnace.cook_time_ticks`;
- furnace recipe only; no blast-furnace, smoker, campfire or stonecutter recipe is registered.

### Registration and conflicts

`Server#addRecipe` is called for every mapping and the key is added to the internal removal list regardless of the boolean result returned by Bukkit. The implementation does not log or branch on duplicate-key registration failure.

If another plugin already owns the same key, registration behaviour follows Bukkit/Paper. On disable, this feature calls `removeRecipe` for every generated key, so key collisions should be avoided.

## Lifecycle and ordering

Initialization:

1. register `BetterCoralListener` unconditionally while the feature is enabled;
2. read `furnace.enabled`;
3. when true, construct `CoralRecipes`, copy cook-time/experience into fields, create the twenty mappings, and register every recipe.

Disable:

1. when a recipe helper exists, remove every stored recipe key;
2. clear the helper's key list;
3. allow the feature lifecycle manager to unregister the fade listener.

The fade protection is always active when the feature is enabled, even if furnace recipes are disabled.

There is no repeating task, asynchronous work or mutable player state.

## Persistence, database and messaging

BetterCoral has no persistent state. Recipe registration lives in Bukkit's runtime recipe registry and is reconstructed on every feature enable. It does not use DataProvider, filesystem data files, Redis, proxy messages, APIs, or PAPI.

## Developer source map

- Defaults/lifecycle: `features/bettercoral/BetterCoral.java`
- Fade prevention: `features/bettercoral/listener/BetterCoralListener.java`
- Recipe mappings/registration: `features/bettercoral/recipe/CoralRecipes.java`
- Metadata: `features/bettercoral/meta/Meta.java`

## Operational verification

1. Place every live coral form in conditions where vanilla would kill/fade it and confirm it remains live.
2. Confirm dead coral and unrelated fade events are not cancelled.
3. Have another plugin cancel `BlockFadeEvent` before `HIGHEST` and verify this listener does not perform additional work.
4. With furnace recipes enabled, inspect all twenty recipes and confirm family/form mappings.
5. Test the configured two-tick default cook time and experience output.
6. Disable only `furnace.enabled` and verify coral preservation remains active while recipes disappear after reload.
7. Disable/reload the feature and confirm generated recipe keys are removed and re-added exactly once.
8. Test alongside recipe-management plugins for key conflicts.
9. Test WorldEdit/direct block replacement separately because those paths may not fire fade events.

## Troubleshooting

- **Coral still dies:** determine whether the change fired `BlockFadeEvent`. Direct block replacement or another plugin's custom mechanic can bypass this listener.
- **Only some coral is protected:** verify the material is one of the twenty live vanilla variants; custom coral blocks are not included.
- **Recipes cook in 20 ticks instead of 2:** the runtime node may have failed integer conversion and used the constructor fallback of `20`.
- **Recipe is missing:** check `furnace.enabled`, duplicate namespaced keys, and whether another plugin clears/rebuilds the recipe registry after ServerFeatures initialization.
- **Recipe remains after disable:** inspect external recipe plugins and confirm the key namespace/path; this feature removes every key it generated.
- **World-specific behaviour is needed:** no world filter exists. Add an explicit world check to the fade listener rather than documenting a nonexistent config option.
