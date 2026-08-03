# BetterCoral

> Paper · Feature name `BetterCoral` · disabled by default

BetterCoral keeps every vanilla live coral variant alive when placed outside water and optionally lets players deliberately dry coral in a furnace.

## Behaviour

- Cancels vanilla `BlockFadeEvent` drying for all five coral families.
- Covers coral blocks, coral plants, floor fans, and placed wall fans.
- Registers furnace recipes for the 15 inventory-item forms: five blocks, five plants, and five fans.
- Wall-fan block states are protected after placement but are intentionally not registered as recipes because wall-fan materials are not valid inventory items. A wall placement uses the corresponding normal coral-fan item, which can be smelted normally.
- Each recipe returns exactly one matching dead-coral equivalent.

Examples:

- `TUBE_CORAL_BLOCK` → `DEAD_TUBE_CORAL_BLOCK`
- `BRAIN_CORAL` → `DEAD_BRAIN_CORAL`
- `FIRE_CORAL_FAN` → `DEAD_FIRE_CORAL_FAN`

## Configuration

File: `plugins/ServerFeatures/features/BetterCoral/config.yml`

| Key | Default | Meaning |
|---|---:|---|
| `enabled` | `false` | Enables the complete feature. |
| `furnace.enabled` | `true` | Enables live-to-dead coral furnace recipes. Fade prevention remains active when this is disabled. |
| `furnace.cook_time_ticks` | `200` | Shared furnace cook time. Runtime values are clamped to `1..72000` ticks. Existing configs keep their configured value. |
| `furnace.experience` | `0.0` | Experience per result. Negative values are clamped to zero. |

Configuration is read when the feature initializes; reload or re-enable the feature after changing it.

## Recipe lifecycle

Recipes use stable plugin-scoped keys:

```text
serverfeatures:coral_dry_<live_material>
```

Before registration, BetterCoral removes a stale recipe with the same key. This makes plugin reloads deterministic. Only successfully registered keys are tracked and removed during feature shutdown.

## Production notes

The old implementation attempted to register 20 recipes, including the five wall-fan block-state materials. Those materials cannot exist as inventory items and could cause invalid `ItemStack`/recipe registration errors. The production implementation separates protected block states from valid smelting inputs and uses one canonical conversion table to prevent the listener and recipe set from drifting apart.

There are no commands, permissions, database records, Redis messages, APIs, player settings, or PlaceholderAPI placeholders.

## Verification

1. Place all live coral blocks, plants, floor fans, and wall fans outside water; they must remain alive.
2. Smelt one of each of the 15 obtainable live coral items; each must yield the matching dead form.
3. Confirm normal coral-fan items can still be placed on walls and remain alive there.
4. Disable `furnace.enabled`; drying protection must remain active and recipes must be absent after reload.
5. Re-enable/reload repeatedly; recipes must not duplicate or throw duplicate-key errors.
6. Verify unrelated `BlockFadeEvent` behavior, such as ice or snow fading, remains unchanged.
