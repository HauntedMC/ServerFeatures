# JoinItems

> Paper · Feature ID `joinitems` · disabled by default

## Overview

Gives players configured utility/menu items on join and controls their intended hotbar interaction behavior.

## Commands and permissions

No command is registered.

## Configuration

Define each item, slot, overwrite policy, custom model/components, click action or command, movement/drop restrictions, permission, and eligible worlds in `features/JoinItems/`.

## Integrations and placeholders

Actions may dispatch configured commands through the normal server command system. No feature-specific PlaceholderAPI expansion is registered.

## Runtime and developer notes

Materialize items only after the player inventory is ready. Identify feature items with stable persistent data instead of display text alone. Prevent only the configured move/drop behavior and avoid deleting unrelated items when a target slot is occupied.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/joinitems/JoinItems.java`.

## Troubleshooting

Document hotbar slot ownership and resolve conflicts with other lobby/menu plugins explicitly.
