# Backup

> Paper · Feature ID `backup` · disabled by default

## Overview

Runs controlled backend backup work with feature-owned scheduling and shutdown cleanup.

## Commands and permissions

No stable player-facing command is registered by the feature entry point.

## Configuration

Use `features/Backup/config.yml` for schedule, target path, retention, included data, and command/process behavior. Schedule I/O-heavy work away from peak load and keep the target outside the live world tree where possible.

## Integrations and placeholders

No feature-specific PlaceholderAPI expansion is registered. The backup destination must be writable by the server process.

## Runtime and developer notes

Scheduled jobs belong to the feature lifecycle and must be cancelled or settled before disable. A backup should use a consistent server-save boundary and must never report success before the target is durable. Retention cleanup must be constrained to the configured backup directory.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/backup/Backup.java`.

## Troubleshooting

Check free disk space, directory ownership, archive/process exit status, and whether a panel-level backup overlaps the feature schedule.
