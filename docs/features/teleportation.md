# Teleportation

> Paper · Feature ID `teleportation` · disabled by default

## Overview

Provides safe random teleportation and explicit coordinate teleportation with validation and cooldown policy.

## Commands and permissions

- `/randomtp` searches for and teleports to a safe random destination using the feature's random-teleport permission.
- `/tppos <x> <y> <z> [world]` teleports to coordinates using the feature's coordinate-teleport permission.

## Configuration

Configure worlds, radius/bounds, maximum attempts, safe-block rules, cooldown, warm-up, cost, and cancellation on movement/damage in `features/Teleportation/`.

## Integrations and placeholders

Uses Paper world/chunk/teleport APIs and normal protection cancellation. No PlaceholderAPI expansion is registered.

## Runtime and developer notes

Safe-location search is bounded and may prepare data asynchronously, but final chunk/world access and teleport occur on the correct server thread. Recheck player/world/feature generation before completion. Never load unbounded chunks or retry forever.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/teleportation/Teleportation.java`.

## Troubleshooting

Repeated failure means bounds or safety predicates are too restrictive for the target world.
