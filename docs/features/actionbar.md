# Actionbar

> Paper · Feature name `Actionbar` · feature package `features.actionbar` · disabled by default

Actionbar is the feature-level controller for ServerFeatures' shared `ActionBars` HUD service. It can start a repeating, configuration-driven action-bar cycle and can broadcast one-off or timed administrator messages. The shared HUD service owns the actual per-player delivery and cycle arbitration; this feature owns the command, the feature-local cycle handle, and the translation from `local/actionbars.yml` into the API model.

## Behaviour at a glance

- A configured cycle is **not** started automatically when the feature enables. An authorised sender must run `/actionbar start`.
- Only one cycle handle can be active for this feature at a time.
- Timed manual messages pause the cycle through `PauseMode.PAUSE_CYCLE`; one-shot messages do not create a timed override.
- Cycle entries are rendered separately for each player so localization and player-specific placeholders can differ.
- Manual text is also formatted per player and supports mixed legacy/MiniMessage input plus PlaceholderAPI processing.
- There is no database state, Redis messaging, persisted running flag, or per-player opt-out in this feature.

## Commands and permissions

Actionbar uses Paper's Brigadier command API. The command tree is registered through the feature lifecycle manager.

| Syntax | Permission | Sender | Behaviour |
|---|---|---|---|
| `/actionbar start` | `serverfeatures.feature.actionbar.use` and `serverfeatures.feature.actionbar.command.start` | Player or console | Builds a fresh cycle from `local/actionbars.yml` and starts it through the global `ActionBars` service. Refuses when this feature's cycle is already active. |
| `/actionbar stop` | `serverfeatures.feature.actionbar.use` and `serverfeatures.feature.actionbar.command.stop` | Player or console | Cancels this feature's active cycle handle. Refuses when no active handle exists. |
| `/actionbar send <seconds> <message...>` | `serverfeatures.feature.actionbar.use` and `serverfeatures.feature.actionbar.command.send` | Player or console | Broadcasts the message once when `seconds = 0`, or for the requested duration when positive. |

The root permission is evaluated before Brigadier exposes or executes any subcommand. Each subcommand then performs its own permission check. Granting a subcommand permission without the root permission is therefore insufficient.

### Argument rules and suggestions

- `seconds` is an integer constrained by Brigadier to `0..3600` inclusive.
- Suggestions are `0`, `3`, `5`, `10`, `30`, `60`, `120`, and `300`, with tooltips explaining one-shot versus timed delivery.
- `message` is a greedy string and consumes the rest of the command.
- The message suggestion tooltip is rendered through the same Adventure formatting utilities used elsewhere in ServerFeatures.
- There are no aliases.

### Command result messages

| Message key | Variables | Condition |
|---|---|---|
| `actionbar.started` | none | Cycle started successfully. |
| `actionbar.stopped` | none | Cycle stopped successfully. |
| `actionbar.already_running` | none | `/actionbar start` while this feature already owns an active handle. |
| `actionbar.not_running` | none | `/actionbar stop` without an active handle. |
| `actionbar.sent_once` | `{message}` | One-shot message sent with `seconds = 0`. |
| `actionbar.sent_timer` | `{time}`, `{message}` | Timed message sent. `{time}` is the integer number of seconds. |

`actionbar.usage`, `actionbar.send_usage`, and `actionbar.invalid_time` remain available in the default message map for compatibility/localization, but Brigadier's command shape and integer validation normally prevent those legacy-style usage paths from being reached by this implementation.

## Feature configuration

Feature file: `plugins/ServerFeatures/features/Actionbar/config.yml`.

| Key | Default | Meaning |
|---|---:|---|
| `enabled` | `false` | Enables registration of the feature service and Brigadier command. |
| `message_interval` | `0` ticks | Present in the feature config for compatibility, but the active cycle builder reads `message_interval` from `local/actionbars.yml`, not from this feature config. Changing only this value does not change the cycle gap in the current implementation. |

The distinction above is important: cycle content and effective timing live in the local actionbar store described below.

## `local/actionbars.yml` cycle configuration

The feature opens a `ConfigView` for `local/actionbars.yml` with default-copying enabled. Its root contract is:

```yaml
message_interval: 100
messages:
  example:
    message_key: example
    duration: 100
```

### Root keys

| Key | Type/default | Behaviour |
|---|---|---|
| `message_interval` | integer ticks, default `100` | Gap between cycle entries. Converted to whole seconds by ceiling division and clamped to at least zero. |
| `messages` | map | Ordered/config-service child map used to construct entries. Each direct child becomes one cycle entry. |

### Per-entry keys

For every child under `messages.<id>`:

| Key | Type/default | Behaviour |
|---|---|---|
| `message_key` | string, defaults to the child `<id>` | Localization suffix. The final key is `actionbar.<message_key>`. |
| `duration` | long ticks, default `100` | Entry display time. Converted to whole seconds using ceiling division and then clamped to at least zero. |

The child ID is only a configuration identifier unless `message_key` is omitted, in which case the ID is used as the localization suffix.

### Tick-to-second conversion

The shared ActionBars API is called with seconds, while the file uses ticks. Conversion is:

```text
seconds = ticks <= 0 ? 0 : (ticks + 19) / 20
```

Consequences:

- `1..20` ticks become `1` second.
- `21..40` ticks become `2` seconds.
- zero and negative values become `0` seconds.
- fractional seconds are always rounded upward, never truncated.

### Empty-message fallback

When `messages` has no children, the feature builds a single five-second entry from `actionbar.default`. The duration is derived from the hard-coded fallback of `100` ticks. The normal configured `message_interval` still supplies the gap.

### Reload semantics

A cycle is built only when `startCycle()` is called. Editing `local/actionbars.yml` does not mutate an already-built active cycle. Stop and start the cycle, or reload the feature through the supported lifecycle, to rebuild it from current configuration.

## Text formatting and PlaceholderAPI

### Configured cycle entries

Configured entries are obtained from the feature localization handler for each target player:

```text
actionbar.<message_key>
```

This means they use ServerFeatures localization audience selection and any variables/placeholders supported by that localization pipeline.

### Manual messages

`/actionbar send` creates a `Function<Player, Component>` and formats separately for every online player:

1. the raw command text enters `TextFormatter` as `MIXED_INPUT`;
2. `PlaceholderAPIHook.applyPlaceholders(text, player)` runs during preprocessing;
3. the result is converted to MiniMessage;
4. MiniMessage is deserialized with all default component features enabled;
5. URL auto-linking is enabled;
6. the resulting component is delivered through the shared ActionBars service.

Because PlaceholderAPI processing is per player, `%player_*%` and other player-context placeholders may produce different output for each recipient. Expensive third-party placeholders are evaluated for every recipient when the message is created or refreshed by the underlying HUD service; use them carefully on large networks.

This feature does not register its own PlaceholderAPI expansion.

## Shared ActionBars integration

The feature calls the static global API `ActionBars.service()`.

### Cycle start

`startCycle()`:

1. returns immediately if `isCycleRunning()` is true;
2. reads `local/actionbars.yml` and builds a new immutable `ActionBarCycle`;
3. calls `ActionBars.service().startCycle(cycle)`;
4. stores the returned `ActionBarCycleHandle`.

`isCycleRunning()` requires both a non-null handle and `handle.isActive()`. A stale inactive handle therefore does not block a new cycle start.

### Cycle stop

`stopCycle()` cancels the handle when present and then clears the reference. Calling it repeatedly is safe.

### Manual broadcast modes

- `seconds <= 0`: `sendOnceBroadcastPerPlayer(perPlayer)`.
- `seconds > 0`: `sendBroadcastPerPlayer(perPlayer, seconds, PauseMode.PAUSE_CYCLE)`.

A timed broadcast asks the global HUD service to pause the normal cycle while the override is active. Arbitration against action bars created by other features is a responsibility of that shared API, not local state in this feature.

## Ordering, concurrency and event model

Actionbar registers no Bukkit event listeners and performs no database or Redis work.

Command callbacks execute through Paper's command infrastructure. The feature itself does not create asynchronous work. Per-player formatting functions are passed to the shared HUD service, which controls when recipients are evaluated and how delivery is scheduled. Feature code must therefore avoid capturing mutable objects that outlive the feature; the current function captures only the feature formatting facilities and raw command text.

Start/stop ordering is local and explicit:

- the handle is assigned only after the shared service returns it;
- stop cancels before clearing the reference;
- disable invokes stop before discarding the feature service.

There is no persisted ownership marker. After a full server restart, the cycle remains stopped until an administrator runs `/actionbar start` again.

## Persistence, database and messaging

Actionbar has:

- no DataProvider registration;
- no database tables/entities/repositories;
- no Redis channels or message contracts;
- no cross-server broadcast;
- no persistent record of whether a cycle was running.

Messages are broadcast only to players visible to the local Paper server's shared ActionBars service.

## Lifecycle

Initialization order:

1. instantiate `ActionbarFeatureService`;
2. open the `local/actionbars.yml` `ConfigView` in its constructor;
3. register the Brigadier command.

The cycle is not started during initialization.

Disable order:

1. call `service.stopCycle()` so the global API no longer references this feature's cycle;
2. set the service field to `null`;
3. allow the feature lifecycle manager to unregister the Brigadier command and other scoped resources.

A failed or partial initialization cannot leave a cycle running because no cycle is created until command execution.

## Developer source map

- Feature entry point/default messages: `features/actionbar/Actionbar.java`
- Brigadier command: `features/actionbar/command/ActionbarCommand.java`
- Cycle/config/formatting service: `features/actionbar/internal/ActionbarFeatureService.java`
- Shared API types: `serverfeatures-api/.../ui/hud/actionbar/`
- Placeholder hook: `serverfeatures-api/.../hook/PlaceholderAPIHook.java`
- Feature metadata: `features/actionbar/meta/Meta.java`

When changing the shared ActionBars API, verify this feature's assumptions about per-player functions, handle activity, timed-message pause mode, and seconds-based durations.

## Operational verification

1. Enable the feature and confirm no action bar begins automatically.
2. Verify the root and each subcommand permission independently, including console execution.
3. Start a non-empty configured cycle and check entry order, duration rounding and gap rounding.
4. Remove all configured messages and verify the five-second `actionbar.default` fallback.
5. Run `start` twice and verify the second call reports `actionbar.already_running` without creating another handle.
6. Send a zero-second manual message and confirm it appears once without a timed override.
7. Send a positive-duration message and confirm the normal cycle pauses and later resumes.
8. Test legacy colour codes, MiniMessage, URLs and player-specific PlaceholderAPI values in a manual message.
9. Edit `local/actionbars.yml` while a cycle is running and verify changes appear only after stop/start.
10. Disable the feature while a cycle or timed message is active and verify no feature-owned repeating cycle remains.

## Troubleshooting

- **Changing `features/Actionbar/config.yml:message_interval` has no effect:** the active implementation reads the interval from `local/actionbars.yml`.
- **Cycle does not start after enabling:** expected; run `/actionbar start` with both required permissions.
- **New config is ignored:** stop and start the cycle so it is rebuilt.
- **An entry shows the wrong text:** check `messages.<id>.message_key`; the localization lookup is `actionbar.<value>`, or `actionbar.<id>` when omitted.
- **Short tick durations look too long:** conversion rounds up to whole seconds because the ActionBars API accepts seconds.
- **Placeholder output is identical or empty:** verify PlaceholderAPI and the expansion providing the placeholder are installed; manual messages apply placeholders per player, while localized cycle entries depend on the localization pipeline.
- **Action bars conflict with another feature:** inspect the shared ActionBars service and its pause/priority policy. This controller only requests a cycle and uses `PAUSE_CYCLE` for timed manual overrides.
