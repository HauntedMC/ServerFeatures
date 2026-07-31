# Nametags

> Paper · Feature ID `nametags` · disabled by default

## Overview

Renders multi-line, per-viewer nametags with delayed world readiness, visibility filtering, reliable entity attachment, refresh, and cleanup.

## Commands and permissions

- `/nametag ...` exposes diagnostics or administration implemented by `NametagCommand`; protect staff operations with the feature's nametag permissions.

## Configuration

Configure rendered lines, offsets, refresh cadence, initial/world-change spawn delay, tracking distance, visibility rules, and template formatting in `features/Nametags/`.

## Integrations and placeholders

Coordinates with PlaceholderAPI/shared formatting, Vanish, Glow, scoreboard teams, and the player lifecycle. It does not register its own PAPI identifier.

## Runtime and developer notes

Create viewer-specific display/fake entities only after the viewer's client world is ready. Generation fencing prevents delayed tasks from reviving retired entities. Teleport, respawn, world/region change, quit, reload, observer visibility, and fast movement trigger reconciliation. Disable must remove every entity and cancel every pending spawn/update.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/nametags/Nametags.java`.

## Troubleshooting

Floating or detached tags indicate stale viewer/entity state; inspect reconciliation around teleport/world changes and competing scoreboard/entity systems.
