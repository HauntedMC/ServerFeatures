# Sanctions

> Paper · Feature ID `sanctions` · disabled by default

## Overview

Enforces shared network sanctions on the backend, especially mute/chat restrictions and synchronized moderation state.

## Commands and permissions

No authoritative sanctions command is registered on Paper; staff issue sanctions through ProxyFeatures.

## Configuration

Configure DataProvider access, cache/freshness behavior, enforcement messages, and outage fail-open/fail-closed policy in `features/Sanctions/`.

## Integrations and placeholders

Consumes ProxyFeatures/shared sanction contracts and DataRegistry identity. No PlaceholderAPI expansion is registered.

## Runtime and developer notes

The mute/chat listener reads cached authoritative state without blocking the chat event. Redis/database updates invalidate or replace cache entries deterministically. During disable, stop callbacks before releasing data access so retired state cannot be mutated.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/sanctions/Sanctions.java`.

## Troubleshooting

When proxy commands succeed but backend enforcement lags, inspect message/database propagation, identity normalization, and cache invalidation.
