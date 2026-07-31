# LagMonitor

> Paper · Feature ID `lagmonitor` · disabled by default

## Overview

Samples server performance indicators and surfaces configured lag warnings or operational diagnostics.

## Commands and permissions

No feature command is registered by the current entry point.

## Configuration

Configure sample interval, TPS/MSPT thresholds, notification permission/audience, warning cooldown, and logging in `features/LagMonitor/`.

## Integrations and placeholders

Uses Paper/server performance metrics. No feature-specific PlaceholderAPI expansion is registered.

## Runtime and developer notes

Use platform metrics rather than expensive world/entity scans. Keep sampling lifecycle-owned and warning delivery rate-limited. The feature reports symptoms; it should not automatically kill entities or mutate gameplay unless that behavior is explicitly configured and documented.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/lagmonitor/LagMonitor.java`.

## Troubleshooting

Correlate warnings with spark/timings and GC/host metrics to find the root cause.
