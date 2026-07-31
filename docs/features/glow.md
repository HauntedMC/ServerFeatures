# Glow

> Paper · Feature ID `glow` · disabled by default

## Overview

Provides a GUI for selecting a permitted glow color and keeps the active glow presentation through player lifecycle events.

## Commands and permissions

- `/glow` opens the menu and requires `serverfeatures.feature.glow.use`.
- `/glow remove` removes the current glow and reports whether one was active.

## Configuration

Configure available colors, menu layout, item presentation, permissions, persistence, and localized feedback in `features/Glow/`.

## Integrations and placeholders

Uses scoreboard-team based glow presentation and the feature's glow handler. No PlaceholderAPI expansion is registered.

## Runtime and developer notes

One handler owns active glow state. Reapply presentation on join/relevant visibility changes and remove feature-owned team/state on disable. Coordinate scoreboard ownership with Nametags and Scoreboard to prevent team replacement.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/glow/Glow.java`.

## Troubleshooting

Incorrect colors or lost glow usually indicate another plugin reassigning scoreboard teams.
