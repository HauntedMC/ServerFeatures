# Actionbar

> Platform: **Paper** · Feature ID: `actionbar` · Status: **implemented** · Default: **disabled**

## Overview

Continuously or periodically renders configured action-bar information for players.

The feature is enabled through the normal feature-management workflow or by setting `enabled: true` in `features/Actionbar/config.yml`. Treat the generated configuration and localization files from the running build as authoritative for exact defaults.

## Commands and permissions

This feature registers no player- or staff-facing command.

Permissions are evaluated at execution time. Hiding a command from suggestions never replaces its permission check, and console/player-only limitations are documented by the command implementation.

## Configuration

- Configure text/templates, eligibility, refresh interval, and placeholder evaluation.

Configuration is loaded with the feature lifecycle. Prefer the supported feature reload/management path over editing files while the feature is actively mutating state.

## Integrations, placeholders, and variables

- Can resolve shared placeholders and PlaceholderAPI values through the feature's formatting service.

Localized messages and configured component templates may expose context-specific variables such as player names, server names, reasons, counts, durations, or command outcomes. Keep variables already present in the generated message file when translating a message.

## Runtime behavior and internals

- The service caches or reuses formatted state where possible and owns its repeating task through the lifecycle manager.

All commands, listeners, tasks, subscriptions, services, entities, and caches created by the feature should be registered through its lifecycle scope. A disable or failed initialization must leave no active callback that can mutate retired state.

## Developer reference

- Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/actionbar/Actionbar.java`
- Metadata: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/actionbar/meta/Meta.java`
- Configuration root: `features/Actionbar/config.yml`
- Localization root: `features/Actionbar/messages.yml`
- Add tests beside the affected module under `src/test/java` and cover enable, disable, reload, and dependency-loss behavior where applicable.
- When adding a command, document its complete syntax, aliases, sender restrictions, tab completion, and every permission node here.
- When adding an API, Redis/database contract, PAPI placeholder, or message variable, update this page in the same pull request.

## Troubleshooting

- Use a conservative refresh interval when templates contain database-backed third-party placeholders.
