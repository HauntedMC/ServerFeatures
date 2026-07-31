# CommandLogger

> Paper · Feature ID `commandlogger` · disabled by default

## Overview

Records commands executed on the Paper server for moderation and security auditing.

## Commands and permissions

No command is registered by this feature; it observes command execution.

## Configuration

Configure DataProvider connection/access, excluded commands, argument redaction, console/player handling, server context, and stored audit fields in `features/CommandLogger/`.

## Integrations and placeholders

Uses DataProvider persistence and shared command-log entities/contracts where configured. No PlaceholderAPI expansion is registered.

## Runtime and developer notes

Normalize and redact before asynchronous persistence. Logging failures must never block command execution. Capture whether the proxy or backend actually handled the command and avoid storing authentication tokens or other secrets. Shutdown stops new captures before releasing data access.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/commandlogger/CommandLogger.java`.

## Troubleshooting

Review exclusions and redaction whenever a new token-, password-, or recovery-bearing command is introduced.
