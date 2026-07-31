# Balloons

> Paper · Feature ID `balloons` · disabled by default

## Overview

Offers cosmetic balloon companions through an inventory GUI and maintains their attachment while players move through normal gameplay states.

## Commands and permissions

- `/balloons` opens the selection/removal menu. Individual balloons and use access can be permission-gated by the feature.

## Configuration

Configure balloon definitions, materials or custom-model data, menu slots, offsets, movement cadence, availability permissions, and messages in `features/Balloons/`.

## Integrations and placeholders

A compatible resource pack is required for custom models. No feature-specific PlaceholderAPI expansion is registered.

## Runtime and developer notes

The manager owns all balloon entities and must remove them on quit, world change, death, feature disable, and failed initialization. Teleports and tracking-range transitions require a reconciliation rather than continuing an old movement task against a retired entity.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/balloons/Balloons.java`.

## Troubleshooting

Detached or invisible balloons normally indicate entity-tracking, teleport, world-change, or missing resource-pack state.
