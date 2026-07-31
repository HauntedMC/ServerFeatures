# ChatFilter

> Paper · Feature ID `chatfilter` · disabled by default

## Overview

Filters local chat against configured normalization and content rules while preserving the wider Paper chat pipeline.

## Commands and permissions

No command is registered.

## Configuration

Configure blocked words/patterns, normalization, replacement or cancellation action, bypass permissions, cooldowns, and staff/player notifications in `features/ChatFilter/`.

## Integrations and placeholders

No feature-specific PlaceholderAPI expansion is registered.

## Runtime and developer notes

Filtering belongs in the chat event path and must not perform blocking database or network work. Normalize predictably, keep regexes bounded, and preserve signed-chat/rendering behavior expected by ChatLayout. Respect already-cancelled events according to the documented policy.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/chatfilter/ChatFilter.java`.

## Troubleshooting

Test Unicode, formatting codes, punctuation insertion, repeated characters, and false positives before expanding production rules.
