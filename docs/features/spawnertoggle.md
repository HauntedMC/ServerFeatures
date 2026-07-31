# SpawnerToggle

> Paper · Feature ID `spawnertoggle` · disabled by default

## Overview

Allows supported interactions to enable or disable spawner activity without destroying the spawner.

## Commands and permissions

No command is registered; toggling is interaction-driven.

## Configuration

Configure interaction item/action, permission, visual feedback, eligible worlds/types, and persistence in `features/SpawnerToggle/`.

## Integrations and placeholders

No feature-specific PlaceholderAPI expansion is registered. Normal protection-event cancellation remains authoritative.

## Runtime and developer notes

Store toggle state with stable block persistent data or the feature's backing store and revalidate it on placement/chunk load. Ensure break/drop moves the intended state once, and that copied/world-edited blocks cannot silently create invalid records.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/spawnertoggle/SpawnerToggle.java`.

## Troubleshooting

World-edit and chunk-copy tools vary in whether they preserve block persistent data; test the production toolchain.
