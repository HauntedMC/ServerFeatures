# Broadcast

> Paper · Feature name `Broadcast` · feature package `features.broadcast` · disabled by default

Broadcast provides one local administrative command for sending either a chat component or a title/subtitle pair to every player currently online on the Paper backend. It formats mixed colour-code/MiniMessage input into Adventure components and acknowledges successful dispatch to the sender.

It is not network-wide: no ProxyFeatures message, Redis channel, database queue, offline delivery, audience filter, schedule, or history is involved.

## Commands and permissions

| Syntax | Permission | Sender | Behaviour |
|---|---|---|---|
| `/broadcast chat <message...>` | `serverfeatures.feature.broadcast.command.broadcast` | Player or console | Builds one formatted Adventure component and sends it through `Player#sendMessage` to every currently online player. |
| `/broadcast title <title...>` | same | Player or console | Sends a title to every online player. Use the first `|` to separate title and subtitle. |

The command has no aliases. Fewer than two arguments produces `broadcast.usage`. An unknown mode produces `broadcast.noMode` followed by the usage message.

Tab completion suggests `chat` and `title` for the first argument regardless of permission; execution remains permission-protected.

### Raw-message reconstruction

The command joins arguments after the mode using a single space. Original repeated spaces and leading/trailing whitespace are not preserved. Formatting tags containing spaces remain valid when they survive normal command parsing.

## Complete configuration reference

File: `plugins/ServerFeatures/features/Broadcast/config.yml`.

| Key | Default | Meaning and edge cases |
|---|---:|---|
| `enabled` | `false` | Enables registration of `/broadcast`. |
| `title_fade_in` | `20` ticks | Title fade-in input. Read through a direct cast to `int`, then integer-divided by `20` and passed as whole seconds to `Duration.ofSeconds`. Values `1..19` become zero seconds. Wrong/missing types can throw during command execution. |
| `title_stay` | `100` ticks | Title stay duration, converted with integer `ticks / 20`. Default becomes five seconds. |
| `title_fade_out` | `20` ticks | Title fade-out duration, converted with integer `ticks / 20`. |

Although key names and comments describe ticks, the implementation converts them to **whole seconds with truncation**, not nanosecond-accurate tick durations. For example, `39` ticks becomes one second, not 1.95 seconds.

Negative values reach `Duration.ofSeconds` as negative durations and may be rejected by Adventure/title construction. Use non-negative multiples of 20 for predictable behaviour.

There is no configurable prefix, target audience, permission per mode, sound, title separator, formatter mode, or network scope.

## Formatting pipeline

Both modes use the same component conversion:

1. `TextFormatter.convert(raw)`;
2. expect `MIXED_INPUT`, allowing supported colour-code and MiniMessage syntax;
3. convert to MiniMessage text;
4. deserialize with `ComponentFormatter` expecting MiniMessage;
5. enable `ComponentFormatter.ALL_DEFAULTS()`;
6. enable automatic URL linking;
7. produce one immutable Adventure `Component`.

The feature does not explicitly run PlaceholderAPI preprocessing. Placeholder-looking text is not replaced merely because PlaceholderAPI is installed unless the shared formatter itself implements that expansion path.

Malformed/unsupported formatting behaviour follows the shared formatter's error policy. The command does not catch formatting exceptions locally.

## Chat mode

`broadcastChat` builds one component and iterates `Bukkit.getOnlinePlayers()`:

```java
p.sendMessage(messageComponent)
```

Consequences:

- all recipients receive the same component; no per-player localization or placeholders are evaluated;
- the sender receives the broadcast too when they are an online player;
- console can send but is not part of the recipient collection;
- players joining after iteration begins are not guaranteed delivery;
- vanished players are included because the feature applies no vanish filter;
- there is no chat-event emission, so chat filters/loggers listening only to player chat events may not observe it;
- there is no prefix unless the operator includes one in the supplied text.

After iteration, the sender receives `broadcast.sent` even when zero players were online.

## Title mode

The complete remainder is split on the first literal `|` only:

```text
/broadcast title Main title | Subtitle
```

- text before the first separator becomes title;
- text after it becomes subtitle;
- both sides are trimmed;
- without a separator, subtitle is an empty component;
- additional `|` characters remain in the subtitle;
- there is no escaping mechanism for a literal first pipe in the title.

Title and subtitle are formatted independently through the mixed-input pipeline.

For every online player, the command creates/shows:

```java
Title.title(title, subtitle, Title.Times.times(fadeIn, stay, fadeOut))
```

Timing components are calculated once from config but a separate `Title` object is constructed inside the player loop. Existing client titles are replaced according to Adventure/Minecraft title semantics.

The command does not send a clear/reset before the new title, queue titles, or restore a title from another feature afterward.

## Messages and variables

| Key | Variables | Purpose |
|---|---|---|
| `broadcast.usage` | none | Missing mode/message. |
| `broadcast.sent` | none | Successful dispatch acknowledgment. |
| `broadcast.noMode` | none | First argument is neither `chat` nor `title`. |

The actual broadcast content is supplied directly by the command and is not a localization key.

## Event ordering and interoperability

Broadcast registers no Bukkit listener and fires no custom event.

- Chat broadcasts bypass `AsyncChatEvent`/`PlayerChatEvent` because they call `sendMessage` directly.
- Title broadcasts bypass chat systems entirely.
- Other plugins can still intercept outbound packets/components at protocol/client layers, but no ServerFeatures ordering contract exists.
- The command executes synchronously through Bukkit's command dispatch and iterates the current online-player collection on the server thread.

If audit logging is required, use command logging or add an explicit Broadcast event/audit sink; do not assume ordinary chat logging captures these messages.

## Persistence, database and messaging

Broadcast has no:

- DataProvider registration;
- database table/entity/history;
- Redis or plugin-messaging publication;
- ProxyFeatures relay;
- offline recipient queue;
- PlaceholderAPI expansion;
- public feature API.

Every broadcast is best-effort local delivery at command execution time.

## Lifecycle

Initialization registers one feature command. Disable has no explicit cleanup because the feature owns no state/tasks/listeners; the lifecycle manager unregisters the command.

Config values are read when title mode executes, so timing edits may affect the next title command if the config handler reflects live/reloaded data. The feature itself does not trigger config reload.

## Developer source map

- Defaults/messages/lifecycle: `features/broadcast/Broadcast.java`
- Permission, parsing, formatting and delivery: `features/broadcast/command/BroadcastCommand.java`
- Metadata: `features/broadcast/meta/Meta.java`

## Operational verification

1. Verify permission denial from player and console.
2. Send colour-coded, MiniMessage-formatted and URL-containing chat text.
3. Verify all local players, including sender and vanished staff, receive it.
4. Verify no proxy/backend peers receive it.
5. Test title-only, title/subtitle, empty sides, and multiple pipe characters.
6. Test timings `0`, `19`, `20`, `39`, `40`, and negative/invalid values to confirm truncation and failure boundaries.
7. Send with zero players online and confirm the sender still receives acknowledgment.
8. Check command logger/audit behaviour and confirm player chat listeners do not see direct chat broadcasts.
9. Test conflicts with other title/actionbar plugins and observe client replacement ordering.

## Troubleshooting

- **Only this backend receives the message:** expected; no proxy/Redis relay exists.
- **Placeholder text remains literal:** the command does not call PlaceholderAPIHook.
- **Title timing is shorter than configured:** tick values are integer-divided by 20 and converted to whole seconds.
- **Title command throws after config editing:** timing values are directly cast to `int`; ensure keys exist as integer-compatible values.
- **Chat moderation/logging misses the broadcast:** delivery bypasses chat events. Use command logging or add an explicit broadcast audit/event contract.
- **A literal pipe cannot appear in the title:** the first `|` is always the title/subtitle separator.
- **Some recipients should be excluded:** no audience/filter configuration exists; implement an explicit predicate rather than documenting one.
