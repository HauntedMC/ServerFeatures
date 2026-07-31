# VoteReward

> Paper · Feature ID `votereward` · disabled by default

## Overview

Consumes vote events or queued vote state and grants configured rewards, including delayed delivery when a player joins later.

## Commands and permissions

No command is registered.

## Configuration

Configure reward commands/items, service-specific rules, announcements, offline delivery, and audit behavior in `features/VoteReward/`.

## Integrations and placeholders

Consumes the vote pipeline produced by Votifier/native vote listeners and shared network contracts. No PlaceholderAPI expansion is registered.

## Runtime and developer notes

Native-vote and join delivery converge on one idempotent reward path. Use a stable vote identifier and atomically mark delivery so retries/reconnects cannot grant twice. Validate known offline identity through DataRegistry and keep database work off Paper threads.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/votereward/VoteReward.java`.

## Troubleshooting

Trace ingress, persisted vote ID, delivery attempt, and terminal audit instead of replaying a vote blindly.
