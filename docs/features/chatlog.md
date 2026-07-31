# ChatLog

> Paper · Feature ID `chatlog` · disabled by default

## Overview

Persists local chat records and provides a report workflow for moderation review.

## Commands and permissions

- `/chatreport ...` exposes the implemented reporting command tree. Report creation, inspection, and administration are permission-gated separately where implemented.

## Configuration

Configure DataProvider access, retained message context, report limits, redaction/exclusions, and messages in `features/ChatLog/`.

## Integrations and placeholders

Uses DataProvider/DataRegistry identity and persistence contracts. No feature-specific PlaceholderAPI expansion is registered.

## Runtime and developer notes

Capture must not block the chat event. Persist normalized actor, server, content, and correlation/report context asynchronously. During disable, stop accepting new work before closing the feature's data access and let already-admitted writes reach a terminal outcome.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/chatlog/ChatLog.java`.

## Troubleshooting

Database access-policy errors indicate a DataProvider ownership/shared-access mismatch rather than a chat-listener failure.
