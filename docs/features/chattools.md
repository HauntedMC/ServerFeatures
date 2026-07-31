# ChatTools

> Paper · Feature ID `chattools` · disabled by default

## Overview

Provides staff controls for local chat, such as clearing or changing the server's chat state.

## Commands and permissions

- `/chat ...` is the administration root. Individual subcommands use feature-specific ChatTools permissions.

## Configuration

Configure clear-chat behavior, exemptions, state/cooldown rules, and localized notices in `features/ChatTools/`.

## Integrations and placeholders

No feature-specific PlaceholderAPI expansion is registered.

## Runtime and developer notes

The command and chat listener share one authoritative state. Reset or restore state explicitly on disable, and document whether reload preserves a mute/lock. Respect event priority and cancellation so ChatFilter, ChatLayout, and external chat plugins have predictable ordering.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/chattools/ChatTools.java`.

## Troubleshooting

Unexpected bypass or double messages usually indicate permission inheritance or another plugin handling the same chat event at a different priority.
