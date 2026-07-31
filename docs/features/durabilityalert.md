# DurabilityAlert

> Paper · Feature ID `durabilityalert` · disabled by default

## Overview

Warns players when equipped or used items cross configured remaining-durability thresholds.

## Commands and permissions

No command is registered.

## Configuration

Configure percentage or absolute thresholds, eligible item types and slots, cooldown/hysteresis, message/sound/action-bar output, and optional permission in `features/DurabilityAlert/`.

## Integrations and placeholders

No feature-specific PlaceholderAPI expansion is registered.

## Runtime and developer notes

Prefer event-driven checks around damage, equip, inventory, and repair changes instead of scanning every inventory each tick. Track the last warned threshold so one item does not spam the player, and reset state after repair or replacement. Clear player/item state on quit and disable.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/durabilityalert/DurabilityAlert.java`.

## Troubleshooting

Repeated alerts indicate missing hysteresis or that another plugin continuously rewrites item damage/metadata.
