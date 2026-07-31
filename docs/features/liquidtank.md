# LiquidTank

> Paper · Feature ID `liquidtank` · disabled by default

## Overview

Implements persistent block-based tanks for water, lava, milk, honey, experience, dragon breath, food/stew types, and empty state.

## Commands and permissions

- `/liquidtank ...` exposes the implemented give/create/inspect/administration command tree with feature-specific permissions.

## Configuration

Configure capacity, representation, supported tank types, transfer amounts, worlds, persistence, and messages in `features/LiquidTank/`.

## Integrations and placeholders

Uses Paper block/player/world events and the feature's data handler. No PlaceholderAPI expansion is registered.

## Runtime and developer notes

`LiquidTankManager` coordinates block, player, and world listeners. Persisted tank data is separate from loaded runtime objects and must reconcile on chunk/world load and unload. Each tank type encapsulates fill/drain validation so unsupported containers or partial transfers cannot duplicate contents.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/liquidtank/LiquidTank.java`.

## Troubleshooting

State loss after restart normally indicates persistence-path permissions or unload/save ordering; inspect the exact tank identifier and backing data record.
