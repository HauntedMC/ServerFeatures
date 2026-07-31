# InstaSkull

> Paper · Feature ID `instaskull` · disabled by default

## Overview

Provides immediate player-head/skull handling for configured gameplay interactions or deaths.

## Commands and permissions

No command is registered.

## Configuration

Configure eligible events/players, drop or give behavior, ownership/profile metadata, cooldowns, and permissions in `features/InstaSkull/`.

## Integrations and placeholders

Uses Minecraft profile/skull metadata. No feature-specific PlaceholderAPI expansion is registered.

## Runtime and developer notes

Create skull data from already-known/cached profiles where possible and never block the Paper thread on a remote profile request. Preserve UUID/name ownership metadata and validate delivery so a full inventory cannot silently delete an item.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/instaskull/InstaSkull.java`.

## Troubleshooting

Delayed or missing textures generally point to profile/skin cache behavior rather than item creation.
