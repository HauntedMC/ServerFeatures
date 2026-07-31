# Broadcast

> Paper · Feature ID `broadcast` · disabled by default

## Overview

Lets authorized senders publish a formatted message to the current Paper server.

## Commands and permissions

- `/broadcast <message>` sends the message to the local server audience. Access is controlled by the feature's broadcast permission; console use is supported when permitted.

## Configuration

Configure prefix, layout, formatter syntax, and localized feedback in `features/Broadcast/`.

## Integrations and placeholders

Uses Adventure components and the shared localization/formatting pipeline. No feature-specific PlaceholderAPI expansion is registered.

## Runtime and developer notes

The command should build one immutable component and deliver it to the selected audience. Preserve the raw remainder of the command as message content rather than losing spacing or formatting tokens.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/broadcast/Broadcast.java`.

## Troubleshooting

Literal formatting tokens indicate a mismatch between configured syntax and the active component formatter.
