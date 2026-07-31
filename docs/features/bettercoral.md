# BetterCoral

> Paper · Feature ID `bettercoral` · disabled by default

## Overview

Prevents or customizes coral death under the configured world and block conditions.

## Commands and permissions

No command is registered.

## Configuration

Configure eligible worlds, coral block families, and which fade/update transitions are blocked in `features/BetterCoral/config.yml`.

## Integrations and placeholders

No feature-specific PlaceholderAPI expansion is registered.

## Runtime and developer notes

The listener should cancel only the configured coral transitions and leave unrelated block updates untouched. It is event-driven and owns no persistent player state. Register and remove the listener through the feature lifecycle.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/bettercoral/BetterCoral.java`.

## Troubleshooting

World-management plugins that directly replace blocks can bypass normal fade events; inspect the actual block-change source when coral still dies.
