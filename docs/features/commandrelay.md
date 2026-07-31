# CommandRelay

> Paper · Feature ID `commandrelay` · disabled by default

## Overview

Relays allowlisted commands between Paper and proxy/network components with target validation, correlation, replay protection, timeouts, results, and database audit logging.

## Commands and permissions

- `/commandrelay ...` exposes local relay administration/submission operations. Use the dedicated CommandRelay permissions and keep broad execution access staff-only.

## Configuration

Configure Redis channels, sender/publisher identity, allowed targets and command roots, timeout, retry, deduplication window, result handling, and audit persistence in `features/CommandRelay/`.

## Integrations and placeholders

Uses DataProvider Redis messaging plus shared ProxyFeatures API relay messages/entities. No PlaceholderAPI expansion is registered.

## Runtime and developer notes

Every request carries a correlation ID. Validate envelope, target, sender, and allowlist before execution; deduplicate retries/reconnect delivery; and record accepted, rejected, completed, failed, and timed-out terminal outcomes. Subscription callbacks are generation-fenced during disable.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/commandrelay/CommandRelay.java`.

## Troubleshooting

Trace the correlation ID through receive, validation, execution, response, and database audit. A received-but-not-run command is usually an allowlist or target rejection.
