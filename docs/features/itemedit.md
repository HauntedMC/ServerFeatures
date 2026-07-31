# ItemEdit

> Paper · Feature ID `itemedit` · disabled by default

## Overview

Adds safe item-editing behavior, including the feature's anvil-based editing flow, for authorized users.

## Commands and permissions

No FeatureCommand is registered by the current entry point; editing is driven through the supported inventory interaction.

## Configuration

Configure editable properties, input validation, length/formatting limits, eligible items, and permission requirements in `features/ItemEdit/`.

## Integrations and placeholders

No feature-specific PlaceholderAPI expansion is registered.

## Runtime and developer notes

The anvil listener must mutate only supported metadata and preserve unrelated NBT/components. Handle result clicks, shift clicks, number keys, drags, closes, and denied permissions without duplication or item loss. Do not deserialize arbitrary unsafe item data from chat input.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/itemedit/ItemEdit.java`.

## Troubleshooting

Custom-item plugins can overwrite the same components; test metadata round-tripping with every production item provider.
