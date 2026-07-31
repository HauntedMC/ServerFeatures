# Nickname

> Paper · Feature ID `nickname` · disabled by default

## Overview

Stores and applies player nicknames to local display surfaces with validation, caching, asynchronous persistence, and a PlaceholderAPI fallback.

## Commands and permissions

- `/nick ...` exposes the implemented self/other set and clear operations. Apply the feature's separate nickname permissions for self and staff actions.

## Configuration

Configure length, allowed formatting/characters, default/fallback behavior, cache/persistence, and affected display surfaces in `features/Nickname/`.

## Integrations and placeholders

`%serverfeatures_nickname%` returns the cached nickname. If absent, it returns the player's normal name while asynchronously warming the cache; a null player returns an empty value.

## Runtime and developer notes

Placeholder reads never block on database work. The nickname handler is the authoritative cache/persistence layer and must reconcile display state after join or asynchronous updates. Guard late completions after feature disable.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/nickname/Nickname.java`.

## Troubleshooting

The first placeholder read may show the real name until cache warming completes. Check DataProvider access and validation messages when changes do not persist.
