# Spawn

> Paper · Feature ID `spawn` · status **scaffold**

## Overview

`spawn` is reserved for future spawn behavior. The current `Spawn` class is empty and does not extend the feature base class.

## Commands and permissions

No `/spawn` command is implemented or registered by this module.

## Configuration and integrations

There is no runtime configuration, localization, API, listener, task, persistence, or PlaceholderAPI surface to operate today.

## Runtime and developer notes

Do not advertise this feature as working functionality merely because the package and metadata exist. A real implementation must extend the correct feature base, provide metadata/configuration/messages, register all commands/listeners/services through the lifecycle manager, define reload/disable behavior, add tests, and update this page with exact syntax and permissions.

Current class: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/spawn/Spawn.java`.

## Troubleshooting

Enabling the scaffold will not provide spawn teleportation. Use another implemented feature/plugin until this module is completed.
