# AntiRaidFarm

> Paper · Feature ID `antiraidfarm` · disabled by default

## Overview

Restricts configured raid-farm mechanics and gives administrators a command surface for inspecting or changing feature state.

## Commands and permissions

- `/antiraidfarm ...` exposes the implemented administration subcommands. Access is controlled by the feature's AntiRaidFarm command permissions.

## Configuration

Configure eligible worlds/areas, restricted raid triggers, limits, cooldowns, bypass permission, and player feedback in `features/AntiRaidFarm/`.

## Integrations and placeholders

No feature-specific PlaceholderAPI expansion is registered. Protection/region decisions should be respected through the normal event cancellation chain.

## Runtime and developer notes

Raid-related listeners enforce policy at event time. Any per-player, village, or raid counters must be bounded and cleared on terminal raid events, world unload, disable, and reload. Keep checks event-driven rather than scanning all raids every tick.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/antiraidfarm/AntiRaidFarm.java`.

## Troubleshooting

Verify world and region filters before assuming Paper raid events are not firing. Check bypass permissions and competing plugins that cancel the same events.
