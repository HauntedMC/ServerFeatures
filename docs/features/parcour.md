# Parcour

> Paper · Feature ID `parcour` · disabled by default

## Overview

Provides HauntedMC parkour-course gameplay helpers, player run state, checkpoints, timing, and course event handling.

## Commands and permissions

The current entry point does not register a standard `FeatureCommand`; gameplay is driven by configured course interactions/events.

## Configuration

Configure courses/regions, start and finish behavior, checkpoints, timing, allowed movement/teleports, reset rules, rewards, and messages in `features/Parcour/`.

## Integrations and placeholders

May dispatch configured rewards/commands. No feature-specific PlaceholderAPI expansion is registered.

## Runtime and developer notes

Run state is per player and must be cleared on quit, death, disallowed teleport, world change, feature reload, and course removal. Checkpoints are ordered and should not be advanced twice by repeated movement events. Keep timing monotonic and event handling bounded.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/parcour/Parcour.java`.

## Troubleshooting

Stuck runs usually indicate a missed teleport/world-change/reset transition or overlapping course regions.
