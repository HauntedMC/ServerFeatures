# Restart

> Paper · Feature ID `restart` · disabled by default

## Overview

Coordinates announced, cancellable backend restarts, join prevention, paced player evacuation, and the final restart action.

## Commands and permissions

- `/restart ...` starts or schedules the restart flow and exposes status through the implemented command tree.
- `/restart cancel` cancels the active countdown before evacuation is committed.
- Restrict all restart operations to the feature's administration permission.

## Configuration

Configure schedule, countdown milestones, join guard, evacuation delay between players, target/fallback selection, restart command, autoreconnect messaging, and messages in `features/Restart/`.

## Integrations and placeholders

Can coordinate with ProxyFeatures restart/autoreconnect over the network messaging layer. No PAPI expansion is registered.

## Runtime and developer notes

Immediate and scheduled restarts share one state machine. Stop new joins before evacuation; move/kick players with a short interval to avoid overloading fallback servers; execute restart only after evacuation reaches a terminal state. Cancellation and disable retire all countdown tasks atomically.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/restart/Restart.java`.

## Troubleshooting

An external panel restart cannot be cancelled by this feature. Verify proxy messaging and fallback availability when autoreconnect does not complete.
