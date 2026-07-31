# AutoLapis

> Paper · Feature ID `autolapis` · disabled by default

## Overview

Supplies the lapis side of enchanting automatically so players can use configured enchanting workflows without carrying lapis manually.

## Commands and permissions

No command is registered.

## Configuration

Configure whether lapis is virtual or consumed, eligible worlds/inventories, permission requirements, and any amount or slot behavior in `features/AutoLapis/config.yml`.

## Integrations and placeholders

No feature-specific PlaceholderAPI expansion is registered.

## Runtime and developer notes

The enchanting/inventory listener must own only the enchanting slots and preserve vanilla result validation. Shift-click, number-key, drag, close, and plugin-modified enchanting flows must not duplicate virtual lapis or leave real items in decorative slots. Register listeners through the feature lifecycle.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/autolapis/AutoLapis.java`.

## Troubleshooting

Test alongside custom-enchant plugins because they may replace the normal enchanting inventory transaction.
