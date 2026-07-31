# WorldEditVisualizer

> Paper · Feature ID `worldeditvisualizer` · disabled by default

## Overview

Visualizes a builder's WorldEdit selection with temporary particles or display markers.

## Commands and permissions

- `/worldeditvisualizer ...` toggles or configures the sender's visualization through the implemented command tree and feature permission.

## Configuration

Configure style/particle, refresh rate, point/edge density, view range, maximum selection size, and defaults in `features/WorldEditVisualizer/`.

## Integrations and placeholders

Requires a compatible WorldEdit selection API and must fail safely when WorldEdit is unavailable. No PlaceholderAPI expansion is registered.

## Runtime and developer notes

Per-player visualization tasks are bounded and removed on quit, selection clear, disable, or dependency loss. Sample large regions rather than rendering every block edge and perform Bukkit/WorldEdit reads on the correct thread.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/worldeditvisualizer/WorldEditVisualizer.java`.

## Troubleshooting

No visualization usually means no complete WorldEdit selection, missing permission/dependency, or a selection above the configured size cap.
