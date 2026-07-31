# VillagerOptimizer

> Paper · Feature ID `villageroptimizer` · disabled by default

## Overview

Reduces villager processing cost through configurable activation and scheduling rules while preserving intended gameplay as much as possible.

## Commands and permissions

No command is registered.

## Configuration

Configure activation distance, update interval, eligible worlds, profession/trading/restock exceptions, and safety bounds in `features/VillagerOptimizer/`.

## Integrations and placeholders

Uses Paper entity/chunk lifecycle. No PlaceholderAPI expansion is registered.

## Runtime and developer notes

Use bounded scheduling and event-maintained indexes rather than scanning every entity from scratch. Remove unloaded/dead villagers promptly and avoid changing AI/trading state that the feature does not own. Disable restores any temporary feature-owned state.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/villageroptimizer/VillagerOptimizer.java`.

## Troubleshooting

Benchmark representative farms and trading halls; aggressive settings can alter restocking, breeding, or farm timing.
