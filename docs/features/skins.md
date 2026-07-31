# Skins

> Paper · Feature ID `skins` · disabled by default

## Overview

Lets players apply another Minecraft account's skin and lets staff apply or remove skins for online players.

## Commands and permissions

- `/skin <name|remove>` changes the sender's skin; permission `serverfeatures.feature.skins.command.skin.self`.
- `/skin <player> <name|remove>` changes an online target; permission `serverfeatures.feature.skins.command.skin.others`.
- Console must use the two-argument staff form.

## Configuration

`cooldown_seconds` controls repeated self-service lookups (default `60`). Configure messages in `features/Skins/messages.yml`; variables include `{skin}`, `{player}`, and `{seconds}`.

## Integrations and placeholders

Uses the server profile/skin facilities. No PlaceholderAPI expansion is registered.

## Runtime and developer notes

Validate 3–16 character Minecraft names before asynchronous lookup. Only online staff targets are accepted. `SkinState` owns transient applied state and is cleared on disable; late lookups must verify the player and feature generation before applying.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/skins/Skins.java`.

## Troubleshooting

Lookup failures indicate an invalid/nonexistent skin owner or unavailable profile service; cooldown feedback uses `{seconds}`.
