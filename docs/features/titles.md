# Titles

> Paper · Feature ID `titles` · disabled by default

## Overview

Displays configured title and subtitle content to players during the supported join/login flow.

## Commands and permissions

No command is registered.

## Configuration

Configure title, subtitle, fade-in, stay, fade-out, delay, and eligibility in `features/Titles/`. Preserve variables present in generated messages/templates when translating.

## Integrations and placeholders

Uses Adventure title components and may resolve shared placeholder content. No feature-specific PlaceholderAPI expansion is registered.

## Runtime and developer notes

Send after the player is an active audience and cancel delayed delivery if the player disconnects or the feature generation changes. Do not leave repeated join tasks after reload. Keep timing values within client-supported bounds.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/titles/Titles.java`.

## Troubleshooting

Another plugin can immediately replace the title; also check client title settings and configured delay/timings.
