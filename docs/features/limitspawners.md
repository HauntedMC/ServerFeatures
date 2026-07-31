# LimitSpawners

> Paper · Feature ID `limitspawners` · disabled by default

## Overview

Limits spawner placement density or count according to configured world and area rules.

## Commands and permissions

No command is registered.

## Configuration

Configure the counting scope (chunk/radius/claim), maximum count, entity-type exceptions, worlds, bypass permission, and rejection message in `features/LimitSpawners/`.

## Integrations and placeholders

Normal block-place cancellation remains authoritative for protection plugins. No PlaceholderAPI expansion is registered.

## Runtime and developer notes

Placement checks must use bounded chunk/nearby lookups and count only valid spawner blocks. Do not persist duplicate indexes when chunks unload/reload, and respect already-cancelled placement events.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/limitspawners/LimitSpawners.java`.

## Troubleshooting

Make the counting scope visible to players; chunk and radius limits produce different expectations.
