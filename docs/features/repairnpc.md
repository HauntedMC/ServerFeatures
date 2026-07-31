# RepairNPC

> Paper · Feature ID `repairnpc` · disabled by default

## Overview

Provides an NPC-driven item-repair interaction with configured eligibility, cost, limits, and feedback.

## Commands and permissions

No feature command is registered; players interact with the configured NPC.

## Configuration

Configure NPC identity/location matching, repairable item rules, cost calculation, economy behavior, cooldown, permission, and messages in `features/RepairNPC/`.

## Integrations and placeholders

Requires the configured NPC/economy hook where enabled and should fail closed when a required integration is unavailable. No PlaceholderAPI expansion is registered.

## Runtime and developer notes

Validate the NPC, actor, held item, repair amount, and payment before mutating durability. Deduct and repair as one logical operation and protect against double-click/replayed interaction. Preserve unrelated item components.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/repairnpc/RepairNPC.java`.

## Troubleshooting

No interaction normally means the NPC provider is unavailable or the configured NPC identifier does not match.
