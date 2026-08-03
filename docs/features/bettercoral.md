# BetterCoral

> Paper · Feature name `BetterCoral` · feature package `features.bettercoral` · disabled by default · version `1.1.0`

BetterCoral keeps live vanilla coral alive after it is placed outside water and optionally lets players intentionally dry coral in a normal furnace.

The feature has two independent runtime contracts:

1. cancel only the vanilla live-to-matching-dead coral `BlockFadeEvent` transition;
2. register live-item-to-dead-item furnace recipes for every coral form that actually exists as an inventory item.

There are no commands, permissions, player preferences, database records, Redis messages, APIs or PlaceholderAPI placeholders.

## Root cause fixed in 1.1.0

The old recipe table treated all twenty coral block states as furnace items. Five of those states are wall fans such as `TUBE_CORAL_WALL_FAN`. Wall fans are block states only: players hold and smelt the corresponding floor-fan item. Constructing an `ItemStack` result for a dead wall-fan material can therefore throw during feature initialization and leave BetterCoral unusable.

Version 1.1.0 separates the concepts explicitly:

- **drying transitions:** all twenty live block states, including wall fans;
- **furnace conversions:** the fifteen pairs where both input and result are valid inventory items.

Both maps are explicit and immutable. This avoids registry-dependent `Material` classification calls during static initialization; regression tests lock all twenty drying transitions and all fifteen furnace conversions.

## Configuration

File: `plugins/ServerFeatures/features/BetterCoral/config.yml`.

```yaml
enabled: false
furnace:
  enabled: true
  cook_time_ticks: 200
  experience: 0.0
```

| Key | Default | Runtime behavior |
|---|---:|---|
| `enabled` | `false` | Enables coral preservation and the optional recipe service. |
| `furnace.enabled` | `true` | Registers the fifteen valid live-to-dead coral item recipes during feature initialization. Coral preservation remains active when this is false. |
| `furnace.cook_time_ticks` | `200` | Furnace cooking duration. Runtime values are clamped to `1..72000` ticks. Existing explicit configuration values are retained during an upgrade. |
| `furnace.experience` | `0.0` | Experience produced by each recipe. Negative, NaN or infinite values fall back to `0.0`; larger finite values are bounded by Java's float range. |

Configuration is read when the feature initializes. Reload or re-enable BetterCoral after changing furnace settings.

## Coral preservation

The listener runs at `EventPriority.HIGHEST` with `ignoreCancelled = true`.

It compares both sides of the event:

```java
CoralMaterials.isDryingTransition(
    event.getBlock().getType(),
    event.getNewState().getType()
)
```

Only an exact live-to-corresponding-dead pair is cancelled. This prevents vanilla drying while avoiding the previous over-broad behavior that cancelled every `BlockFadeEvent` whose source happened to be live coral. A plugin may still deliberately remove or replace coral with another state.

### Protected world states

All five coral families are protected in all four placed forms:

| Family | Block | Plant | Floor fan | Wall fan |
|---|---|---|---|---|
| Tube | `TUBE_CORAL_BLOCK` | `TUBE_CORAL` | `TUBE_CORAL_FAN` | `TUBE_CORAL_WALL_FAN` |
| Brain | `BRAIN_CORAL_BLOCK` | `BRAIN_CORAL` | `BRAIN_CORAL_FAN` | `BRAIN_CORAL_WALL_FAN` |
| Bubble | `BUBBLE_CORAL_BLOCK` | `BUBBLE_CORAL` | `BUBBLE_CORAL_FAN` | `BUBBLE_CORAL_WALL_FAN` |
| Fire | `FIRE_CORAL_BLOCK` | `FIRE_CORAL` | `FIRE_CORAL_FAN` | `FIRE_CORAL_WALL_FAN` |
| Horn | `HORN_CORAL_BLOCK` | `HORN_CORAL` | `HORN_CORAL_FAN` | `HORN_CORAL_WALL_FAN` |

Placed wall fans remain live because their block-state transition is protected. They do not need a separate wall-fan item recipe: placing a live or dead floor-fan item against a wall produces the matching wall-fan state.

### Boundaries

BetterCoral prevents the vanilla drying transition represented by `BlockFadeEvent`. It does not intercept:

- WorldEdit or another plugin directly calling `Block#setType`;
- explosions, pistons or explicit block removal;
- custom coral materials;
- a plugin-defined transition that does not use the matching dead vanilla material.

Unrelated fading blocks such as ice remain untouched.

## Furnace conversions

When `furnace.enabled` is true, BetterCoral registers fifteen `FurnaceRecipe`s:

- five coral blocks;
- five coral plants;
- five coral-fan items.

Each result preserves both family and form. Examples:

- `TUBE_CORAL_BLOCK` → `DEAD_TUBE_CORAL_BLOCK`
- `BRAIN_CORAL` → `DEAD_BRAIN_CORAL`
- `FIRE_CORAL_FAN` → `DEAD_FIRE_CORAL_FAN`

Wall-fan materials are deliberately excluded because neither the live nor dead wall state is an inventory item.

### Recipe keys

Each recipe uses the ServerFeatures namespace and this path:

```text
coral_burn_<lowercase-live-item-material>
```

Example:

```text
serverfeatures:coral_burn_tube_coral_block
```

Recipes use an exact `RecipeChoice.MaterialChoice`, produce one matching dead item, and apply the shared cook-time and experience settings. BetterCoral does not register blast-furnace, smoker or campfire recipes.

### Registration safety

Recipe registration is transactional for the feature:

1. remove recipes previously registered by the current feature instance;
2. remove a stale recipe under the same ServerFeatures-owned key, if present;
3. register every valid conversion and verify `Server#addRecipe` succeeds;
4. if any registration throws or returns false, remove every recipe added during that attempt and fail initialization;
5. record keys only after the complete set succeeds.

Feature disable removes every recorded recipe. Failed removals are logged, and successful startup logs the final recipe count. This makes feature reload idempotent and prevents a partially registered recipe set.

## Lifecycle

Initialization order:

1. read `furnace.enabled`;
2. sanitize furnace configuration;
3. transactionally register all fifteen recipes when enabled;
4. register the coral-fade listener.

If recipe initialization fails, the recipe service rolls back its partial work and the listener is not activated. When furnace recipes are disabled, the listener still initializes normally.

Disable order:

1. remove registered recipe keys;
2. clear recipe ownership state;
3. allow the feature lifecycle manager to unregister the listener.

There are no repeating tasks, asynchronous operations or persistent feature state.

## Source map

- Defaults and lifecycle: `features/bettercoral/BetterCoral.java`
- Canonical mappings: `features/bettercoral/internal/CoralMaterials.java`
- Fade listener: `features/bettercoral/listener/BetterCoralListener.java`
- Furnace service: `features/bettercoral/recipe/CoralRecipes.java`
- Metadata: `features/bettercoral/meta/Meta.java`
- Mapping regression tests: `features/bettercoral/internal/CoralMaterialsTest.java`
- Listener regression tests: `features/bettercoral/listener/BetterCoralListenerTest.java`

## Production verification

1. Enable BetterCoral and confirm startup reports exactly fifteen registered furnace recipes without an initialization exception.
2. Place each family as a block, plant, floor fan and wall fan outside water; wait longer than the vanilla drying delay and confirm all remain live.
3. Place live coral in water and confirm normal placement remains unchanged.
4. Smelt every block, plant and floor-fan item; confirm each produces exactly one matching dead item.
5. Place a smelted dead fan on the floor and on a wall; confirm Minecraft chooses the correct dead floor/wall state.
6. Confirm no recipe exists for a `*_CORAL_WALL_FAN` material.
7. Set `furnace.enabled: false`, reload, and confirm preservation still works while the custom recipes are absent.
8. Reload or disable/re-enable the feature repeatedly and confirm recipe count remains fifteen with no duplicate-key errors.
9. Set invalid cook-time and experience values and confirm they are sanitized with one startup warning.
10. Trigger an unrelated `BlockFadeEvent`, such as ice melting, and confirm BetterCoral does not cancel it.

## Troubleshooting

- **Feature fails during startup:** verify the deployed build contains BetterCoral `1.1.0`; the old implementation attempted to create wall-fan `ItemStack`s.
- **Coral still becomes dead:** determine whether another plugin replaced the block directly rather than allowing the vanilla matching `BlockFadeEvent`.
- **Recipes cook faster than the documented default:** an existing config may still contain the old explicit `cook_time_ticks: 2`; update it to the desired value.
- **A recipe is missing:** check `furnace.enabled`, startup rollback/error logs and whether another recipe plugin removes ServerFeatures recipes after initialization.
- **Recipes remain after disable:** inspect the exact namespace and any external recipe manager; BetterCoral removes only its recorded ServerFeatures-owned keys.
