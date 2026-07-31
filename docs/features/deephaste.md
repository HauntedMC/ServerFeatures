# DeepHaste

> Paper · Feature ID `deephaste` · disabled by default

## Overview

Boosts a beacon-provided Haste effect when the affected player is below a configured Y level.

## Commands and permissions

No command is registered.

## Configuration

- `y_level`: below this level the boost is applied; default `6`.
- `haste_amplifier`: Paper `FAST_DIGGING` amplifier; default `7`.

## Integrations and placeholders

Requires Paper's `BeaconEffectEvent`. The feature warns and does not initialize on a non-Paper server. No PlaceholderAPI expansion is registered.

## Runtime and developer notes

`BeaconEffectListener` changes only relevant beacon Haste events and owns no persistent player state. Keep the Paper dependency check explicit and lifecycle-register the listener.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/deephaste/DeepHaste.java`.

## Troubleshooting

Confirm the effect originates from a beacon, the server is Paper, and the player's Y coordinate is below the configured cutoff.
