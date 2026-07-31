# ChatLayout

> Paper · Feature ID `chatlayout` · disabled by default

## Overview

Formats Paper chat with rank/name content, hover and click actions, nickname support, and modern signed-chat compatibility.

## Commands and permissions

No command is registered.

## Configuration

Configure the renderer template, hover/click components, placeholders, fallback text, and audience rules in `features/ChatLayout/`.

## Integrations and placeholders

Uses Adventure and the shared placeholder pipeline; configured text may resolve PlaceholderAPI values. Nickname/rank integrations should flow through stable services or placeholders instead of direct plugin casts.

## Runtime and developer notes

The signed-chat listener supplies a renderer rather than packet-rewriting messages. Keep asynchronous chat-event restrictions in mind and avoid unsafe Bukkit calls or blocking lookups. Cache data needed by the renderer before the hot path.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/chatlayout/ChatLayout.java`.

## Troubleshooting

After Paper upgrades, verify the active chat event and renderer contracts when signatures, reporting, or hover/click behavior changes.
