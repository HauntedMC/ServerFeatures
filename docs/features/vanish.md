# Vanish

> Paper · Feature ID `vanish` · disabled by default

## Overview

Provides persisted staff invisibility, per-viewer visibility filtering, interaction protections, tab handling, and reusable `VanishAPI` state.

## Commands and permissions

- `/vanish` toggles self; `/vanish on|off` sets self explicitly. Permission: `serverfeatures.feature.vanish.command.vanish.toggle`.
- `/vanish <player> [on|off]` toggles/sets another online player. Permission: `serverfeatures.feature.vanish.command.vanish.others`.

## Configuration

Configure persistence, notifications, interaction restrictions, visibility/bypass rules, and staff toggle messages in `features/Vanish/`.

## Integrations and placeholders

`%vanish_playercount%` returns the local online count excluding vanished players. Vanish state feeds PlayerCount and other visibility-aware features.

## Runtime and developer notes

One service owns state across join/quit, observer visibility, interaction, tab, world changes, and persistence. Reconcile every viewer rather than issuing uncoordinated hide/show calls. Disable restores feature-owned visibility and retires callbacks/subscriptions safely.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/vanish/Vanish.java`.

## Troubleshooting

A single viewer seeing stale state indicates competing hide/show calls or a missed reconciliation transition.
