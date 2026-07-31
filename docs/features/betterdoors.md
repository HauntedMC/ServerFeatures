# BetterDoors

> Paper · Feature ID `betterdoors` · disabled by default

## Overview

Improves door interaction, including synchronized behavior for valid paired doors and related configured blocks.

## Commands and permissions

No command is registered.

## Configuration

Configure supported door/trapdoor types, pairing rules, sounds or animations, permission requirements, and protection behavior in `features/BetterDoors/config.yml`.

## Integrations and placeholders

No feature-specific PlaceholderAPI expansion is registered. Normal event cancellation should remain authoritative for protection plugins.

## Runtime and developer notes

Interaction handling derives the paired block from orientation, hinge, half, and adjacency and then applies one protection-aware state change. Avoid recursively re-triggering the same interaction or pairing unrelated doors in irregular builds.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/betterdoors/BetterDoors.java`.

## Troubleshooting

Wrong pairing usually means the build does not satisfy the configured orientation/hinge rules or another plugin rewrites the interaction afterward.
