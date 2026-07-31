# Tablist

> Paper · Feature ID `tablist` · disabled by default

## Overview

Builds and refreshes the server tab-list header, footer, and player presentation.

## Commands and permissions

No command is registered.

## Configuration

Configure header/footer templates, refresh interval, sorting/presentation rules, audience conditions, and placeholder processing in `features/Tablist/`.

## Integrations and placeholders

Configured components may resolve shared and PlaceholderAPI values. The feature does not register its own PAPI identifier.

## Runtime and developer notes

Initialize already-online players on enable and remove feature-owned presentation on disable. Build one coherent snapshot per refresh, avoid unsafe asynchronous Bukkit access, and keep expensive placeholder resolution out of very short intervals. Coordinate player-entry presentation with Vanish and Nametags.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/tablist/Tablist.java`.

## Troubleshooting

Stale lines usually indicate a stopped refresh task, viewer eligibility mismatch, or slow third-party placeholder.
