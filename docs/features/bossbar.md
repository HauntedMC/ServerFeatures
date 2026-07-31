# Bossbar

> Paper · Feature ID `bossbar` · disabled by default

## Overview

Shows configured persistent or rotating boss bars to eligible players.

## Commands and permissions

No command is registered.

## Configuration

Configure text, color, overlay, progress, rotation order, audience rules, and refresh timing in `features/Bossbar/config.yml`.

## Integrations and placeholders

Configured components can use the shared formatting/placeholder pipeline. The feature does not register its own PlaceholderAPI expansion.

## Runtime and developer notes

Boss bars and viewers are lifecycle-owned. Join and eligibility changes attach the current bar; quit, disable, and reload remove all feature-owned bars so clients do not retain orphaned UI. Keep refresh intervals conservative when third-party placeholders are expensive.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/bossbar/Bossbars.java`.

## Troubleshooting

If a bar appears stuck, check the rotation task, player eligibility, and whether another plugin immediately replaces or hides the bar.
