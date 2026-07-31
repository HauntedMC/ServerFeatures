# Scoreboard

> Paper · Feature ID `scoreboard` · disabled by default

## Overview

Renders a configurable sidebar scoreboard for eligible players.

## Commands and permissions

No feature command is registered by the current entry point.

## Configuration

Configure title, lines, update interval, world/permission eligibility, duplicate-line handling, and placeholder processing in `features/Scoreboard/`.

## Integrations and placeholders

Configured lines can use shared/PlaceholderAPI values. The feature does not register its own expansion.

## Runtime and developer notes

Own only objectives/teams created by this feature and remove them on disable or player removal. Build one coherent line snapshot per refresh, cap line count/length to client constraints, and avoid recreating the complete scoreboard when only text changes. Coordinate team ownership with Glow and Nametags.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/scoreboard/Scoreboard.java`.

## Troubleshooting

Flicker or lost teams usually indicates full objective recreation or another plugin replacing the player's scoreboard.
