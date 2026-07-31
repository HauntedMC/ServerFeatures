# CustomRecipes

> Paper · Feature ID `customrecipes` · disabled by default

## Overview

Registers configured custom crafting recipes and provides an administration command for recipe inspection or reload operations.

## Commands and permissions

- `/customrecipes ...` exposes the implemented list/inspect/reload administration tree and uses feature-specific permissions.

## Configuration

Define stable namespaced recipe IDs, type/shape, ingredients, result item metadata, discovery behavior, and conflict policy in `features/CustomRecipes/config.yml`.

## Integrations and placeholders

Uses Bukkit/Paper recipe registration. No feature-specific PlaceholderAPI expansion is registered.

## Runtime and developer notes

Recipe keys must be deterministic. Reload removes or replaces only recipes owned by the feature so duplicate keys and stale recipes cannot accumulate. Validate materials, shapes, exact-choice metadata, and result counts before registration and fail the individual recipe without corrupting the rest.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/customrecipes/CustomRecipes.java`.

## Troubleshooting

Duplicate-key errors indicate a prior recipe was not unregistered or another plugin uses the same namespace/key.
