# AFK

> Paper · Feature ID `afk` · disabled by default

## Overview

Tracks player activity, supports manual AFK toggling, automatic AFK state, optional broadcasts, long-idle kicks, and anti-AFK pattern detection.

## Commands and permissions

- `/afk` toggles the sender's state. Permission: `serverfeatures.feature.afk.command.afk.toggle`. Player-only.

## Configuration

Important keys are `afk_timeout_seconds`, `movement_distance_threshold`, `rotation_threshold_degrees`, `broadcast_on_state_change`, `kick_enabled`, `kick_timeout_seconds`, `combo_window_seconds`, and the `anti_afk.*` sampling/lock settings.

## Integrations and placeholders

Registers `AfkAPI`. PlaceholderAPI expansion `afk` provides `%afk_boolean%`, `%afk_binary%`, and `%afk_formatted%`. The formatted value uses `afk.placeholder.afk` or `afk.placeholder.not_afk` and falls back safely when localization fails.

## Runtime and developer notes

Already-online players are bootstrapped when the feature starts. Activity listeners update one authoritative service and a one-second lifecycle task evaluates timeout/kick state. Placeholder reads only report AFK for an online player. All tasks, listeners, commands, API registrations, and state must be retired on disable.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/afk/AFK.java`.

## Troubleshooting

Continuous position/rotation changes from another plugin can prevent AFK. Tune anti-AFK sample count and deviation tolerance if legitimate players are locked too aggressively.
