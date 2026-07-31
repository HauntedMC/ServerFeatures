# Holograms

> Paper · Feature ID `holograms` · disabled by default

## Overview

Creates and manages configured holographic text displays in worlds.

## Commands and permissions

No stable feature command is registered by the current entry point.

## Configuration

Configure hologram IDs, worlds/coordinates, lines, spacing, visibility distance, update interval, and placeholder processing in `features/Holograms/`.

## Integrations and placeholders

Uses the shared Adventure/component formatter and can evaluate configured PlaceholderAPI content when PlaceholderAPI is present. It does not register its own expansion.

## Runtime and developer notes

The manager reconstructs displays from validated configuration and owns every spawned entity/display. World unload, config reload, feature disable, and failed initialization must remove or retire all owned objects and tasks. Limit update frequency and viewer work.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/holograms/Holograms.java`.

## Troubleshooting

Stale duplicate entities usually come from an older build or incomplete cleanup; remove them before changing display implementation type.
