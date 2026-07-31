# EnderFrame

> Paper · Feature ID `enderframe` · disabled by default

## Overview

Allows controlled pickup and placement of End Portal Frames while protecting strongholds and protected claims/regions.

## Commands and permissions

No command is registered.

## Configuration

- `pickup_radius`: radius used by the protection/stronghold checks; default `5`.

## Integrations and placeholders

Detects GriefPrevention and WorldGuard at initialization and respects them when installed. No PlaceholderAPI expansion is registered.

## Runtime and developer notes

Separate block-break and block-place listeners reject stronghold manipulation, another player's claim, and denied WorldGuard locations. A missing optional protection plugin must not break normal operation; an available plugin's denial remains authoritative.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/enderframe/EnderFrame.java`.

## Troubleshooting

If every action is rejected, inspect protection-plugin availability/API compatibility and whether the location is classified as a stronghold.
