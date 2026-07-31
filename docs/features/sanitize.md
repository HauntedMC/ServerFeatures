# Sanitize

> Paper · Feature ID `sanitize` · disabled by default

## Overview

Applies explicit startup hardening and cleanup tasks to server configuration such as `bukkit.yml` and `spigot.yml`.

## Commands and permissions

No command is registered.

## Configuration

Configure which sanitize tasks run and the supported safe values to enforce in `features/Sanitize/config.yml`.

## Integrations and placeholders

Works with server configuration files; no PlaceholderAPI expansion is registered.

## Runtime and developer notes

Use narrow, idempotent tasks rather than arbitrary YAML rewriting. Preserve comments/unknown keys where the configuration library permits, validate paths before write, and log each material change. Do not repeatedly rewrite an already-correct file on every startup.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/sanitize/Sanitize.java`.

## Troubleshooting

Review upstream Paper/Bukkit configuration migrations after upgrades because keys can move or disappear.
