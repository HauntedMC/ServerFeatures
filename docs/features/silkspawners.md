# SilkSpawners

> Paper · Feature ID `silkspawners` · disabled by default

## Overview

Allows controlled spawner pickup, placement, and entity-type management with tool, permission, and protection checks.

## Commands and permissions

- `/silkspawner ...` exposes the implemented administration/give operations and uses feature-specific permissions.

## Configuration

Configure Silk Touch requirement, drop chance, allowed entity types, XP behavior, permissions, and item metadata in `features/SilkSpawners/`.

## Integrations and placeholders

Normal block-event cancellation should preserve claim/region protection. No PlaceholderAPI expansion is registered.

## Runtime and developer notes

Serialize the spawner type with stable item metadata and validate it again on placement. Handle explosions, piston/world-edit operations, full inventories, and cancelled break/place events without duplicate drops or lost blocks.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/silkspawners/SilkSpawners.java`.

## Troubleshooting

Custom spawner plugins can process the same events or use incompatible item metadata; disable duplicate ownership.
