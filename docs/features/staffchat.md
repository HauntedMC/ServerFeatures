# StaffChat

> Paper · Feature ID `staffchat` · disabled by default

## Overview

Receives and displays network staff-chat messages on Paper and can intercept the configured local staff-chat input mode or prefix.

## Commands and permissions

The authoritative command/toggle normally lives in ProxyFeatures. Backend input and delivery use the configured StaffChat permission.

## Configuration

Configure Redis channel, format, permission, local prefix/toggle behavior, server label, and messages in `features/StaffChat/`.

## Integrations and placeholders

Consumes the shared ProxyFeatures staff-chat message contract through DataProvider Redis messaging. No PlaceholderAPI expansion is registered.

## Runtime and developer notes

Use DataProvider's logical self-healing subscription handle. Stop accepting callbacks before unsubscribe/disable, fence callbacks by generation, and never block the Paper thread during reconnect or publish. Delivery is restricted to authorized audiences and should identify the source server.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/staffchat/StaffChat.java`.

## Troubleshooting

One-way delivery normally means channel/type mismatch, missing Redis access, or permission filtering on the receiving side.
