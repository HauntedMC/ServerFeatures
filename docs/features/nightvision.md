# NightVision

> Paper · Feature ID `nightvision` · disabled by default

## Overview

Lets permitted players toggle a stable Night Vision effect.

## Commands and permissions

- `/nightvision` toggles the sender's effect and uses the feature's NightVision permission. Player-only.

## Configuration

Configure effect duration/amplifier, whether state persists across joins, eligible worlds, and messages in `features/NightVision/`.

## Integrations and placeholders

No feature-specific PlaceholderAPI expansion is registered.

## Runtime and developer notes

Track only state owned by this feature. Reapply before the effect expires to avoid flicker, but do not overwrite a stronger effect from another source unless policy explicitly says so. Clear owned effects/state on disable according to persistence policy.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/nightvision/NightVision.java`.

## Troubleshooting

Another potion-management plugin may repeatedly replace or remove the same effect.
