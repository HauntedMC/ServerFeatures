# Portals

> Paper · Feature ID `portals` · disabled by default

## Overview

Provides administrator-defined portal regions that transport players to configured destinations.

## Commands and permissions

- `/portals ...` is the administration root for selection/wand, creation, removal, inspection, or reload operations implemented by `PortalsCommand`. Apply the feature's granular portal permissions.

## Configuration

Configure portal IDs, two-corner regions, worlds, destination type/location/server, cooldown, safety, and selection wand in `features/Portals/`.

## Integrations and placeholders

Uses Paper movement/teleport events and configured destination hooks. No feature-specific PlaceholderAPI expansion is registered.

## Runtime and developer notes

Validate both selection corners and destination availability before activating a portal. Movement handling must avoid repeated triggers while the player remains inside, honor cancelled/protection events, and clear cooldown/selection state on quit and disable.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/portals/Portals.java`.

## Troubleshooting

Verify world names, selection bounds, destination availability, cooldown, and protection cancellation when a portal does not trigger.
