# Votifier

> Paper · Feature ID `votifier` · disabled by default

## Overview

Receives vote notifications on Paper and publishes or persists them for network reward processing.

## Commands and permissions

No command is registered.

## Configuration

Configure listener/source behavior, messaging channel, retry/backoff, publisher identity, and database logging in `features/Votifier/`.

## Integrations and placeholders

Uses shared vote contracts and DataProvider Redis/database access. No PlaceholderAPI expansion is registered.

## Runtime and developer notes

Validate and assign a stable vote identity before publish. Publication retries preserve identity and are bounded. DataProvider's self-healing subscription/publisher lifecycle must not leak callbacks after reload. Stop new work before closing access, but allow admitted audit writes to reach a terminal state.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/votifier/Votifier.java`.

## Troubleshooting

Check ingress plugin token/port first, then shared channel/type/publisher settings and retry/audit records.
