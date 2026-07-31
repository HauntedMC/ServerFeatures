# Whitelist

> Paper · Feature ID `whitelist` · disabled by default

## Overview

Applies HauntedMC-specific backend whitelist admission behavior and localized rejection messages.

## Commands and permissions

No feature-specific command is registered; administration uses the supported configuration/feature workflow or the platform's whitelist controls where applicable.

## Configuration

Configure enabled state, allowed identities/groups, bypass permission, outage policy, and rejection message in `features/Whitelist/`.

## Integrations and placeholders

May use DataRegistry/permission identity depending on configuration. No PlaceholderAPI expansion is registered.

## Runtime and developer notes

Admission checks happen before full join and must not block login threads on unbounded database/network calls. Canonical UUID is authoritative; cache and timeout behavior must have an explicit fail-open/fail-closed policy. Keep proxy and backend maintenance/whitelist rules aligned.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/whitelist/Whitelist.java`.

## Troubleshooting

If allowed players are rejected, inspect UUID mode/forwarding, cache availability, bypass permission, and duplicate proxy admission rules.
