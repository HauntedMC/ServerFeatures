# VersionRecommender

> Paper · Feature ID `versionrecommender` · disabled by default

## Overview

Advises players when their Minecraft client version differs from the server/network recommendation.

## Commands and permissions

No command is registered.

## Configuration

Configure recommended version text or accepted range, delay, permission/audience filters, and notification message in `features/VersionRecommender/`.

## Integrations and placeholders

Uses protocol/client-version information available through Paper or the forwarding/protocol compatibility setup. No PlaceholderAPI expansion is registered.

## Runtime and developer notes

The recommendation is advisory and runs after login so it cannot interfere with protocol negotiation. Cancel delayed messages on disconnect/disable and keep protocol-number to display-version mapping explicit and testable.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/versionrecommender/VersionRecommender.java`.

## Troubleshooting

Synchronize the recommendation with the actual Velocity/ViaVersion support policy after each Minecraft update.
