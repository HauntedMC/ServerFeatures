# NotifyLogin

> Paper · Feature ID `notifylogin` · disabled by default

## Overview

Notifies configured recipients when selected players log in.

## Commands and permissions

No command is registered.

## Configuration

Configure watched identities/groups, recipient permission, delivery delay, server/world filters, and message layout in `features/NotifyLogin/`.

## Integrations and placeholders

Uses normal identity and localization formatting. No PlaceholderAPI expansion is registered.

## Runtime and developer notes

Resolve recipients after login when audiences are available. Keep watched identity matching canonical and avoid exposing vanished or sensitive staff state beyond the configured audience. Delayed tasks must be cancelled or generation-checked on disable and player disconnect.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/notifylogin/NotifyLogin.java`.

## Troubleshooting

Check exact UUID/name matching, recipient permissions, delay, and whether another feature suppresses the login presentation.
