# ChatTools

> Paper · Feature name `ChatTools` · feature package `features.chattools` · disabled by default

ChatTools provides three local administrative actions: lock chat, unlock chat, and clear players' visible chat history by sending blank lines. The lock is a single volatile in-memory boolean for the current Paper backend and is enforced by cancelling `AsyncChatEvent` for non-bypassed players.

It is not a proxy/network chat lock, persistent maintenance flag, channel manager, slow mode, command mute, or database-backed moderation action.

## Commands and permissions

Command root: `/chat`, no aliases.

| Syntax | Permission | Sender | Behaviour |
|---|---|---|---|
| `/chat lock` | `serverfeatures.feature.chattools.command.chat.lock` | Player or console | Sets local lock state true and broadcasts the configured lock notice to all local online players. |
| `/chat unlock` | `serverfeatures.feature.chattools.command.chat.unlock` | Player or console | Sets local lock state false and broadcasts unlock notice. |
| `/chat clear` | `serverfeatures.feature.chattools.command.chat.clear` | Player or console | Sends configured number of empty messages to every local online player, then broadcasts the cleared notice. |

Without/with unknown subcommand, `chattools.usage` is sent. Subcommand tab completion suggests all three values without filtering by permission.

### Chat bypass

`serverfeatures.feature.chattools.bypass`

A player with this permission can send `AsyncChatEvent` messages while locked. It does not grant command permissions and does not hide lock/clear broadcasts.

## Complete configuration reference

File: `plugins/ServerFeatures/features/ChatTools/config.yml`.

| Key | Default | Behaviour and edge cases |
|---|---:|---|
| `enabled` | `false` | Registers `/chat` and lock listener. |
| `clear_lines` | `150` | Number of empty string messages sent to each online player during `/chat clear`. Direct cast to `int`; wrong/missing types can fail command execution. Zero/negative values send no blank lines but still send the cleared broadcast. Very large values can create packet/chat spam and client/server load. |

No config controls initial lock state, bypass permission, message delay, target worlds, maximum clear size, lock persistence, or network synchronization.

## Lock state semantics

The authoritative field is:

```java
private volatile boolean chatLocked = false;
```

- Initial value is always false for a new feature instance.
- `volatile` gives cross-thread visibility between synchronous commands and asynchronous chat events.
- There is no compare-and-set; command checks then writes under normal server command serialization.
- State is not saved to config/database/Redis.
- Backend restart or feature reload unlocks chat.
- Disable does not explicitly set false, but the listener is unregistered and the old instance becomes irrelevant.

### Lock command

1. check lock permission;
2. if already locked, send `chattools.already_locked` only to sender;
3. otherwise set true;
4. iterate current `Bukkit.getOnlinePlayers()` and build/send `chattools.locked_broadcast` per audience.

No actor name is included and no audit/database entry is created.

### Unlock command

Equivalent flow with `chattools.not_locked`, false state, and `chattools.unlocked_broadcast`.

### Clear command

1. check clear permission;
2. read `clear_lines`;
3. for every online player, call `sendMessage("")` in a nested loop;
4. perform a second online-player iteration and send localized `chattools.cleared_broadcast`.

The feature does not actually delete signed chat messages or client history; it pushes previous lines upward. Players with large chat windows/history, mods, logs, screenshots, or reopening chat can still retain/inspect prior content according to client behaviour.

There is no bypass from clear. Every local online player receives the blank lines and notice.

## Chat enforcement event

Listener declaration uses Bukkit defaults:

```java
@EventHandler
public void onAsyncChat(AsyncChatEvent event)
```

Thus priority is `NORMAL`, and `ignoreCancelled` defaults to false.

Flow:

1. return when unlocked;
2. return when sender has bypass;
3. cancel event;
4. send `chattools.locked_cant_chat` to sender.

Ordering implications:

- ChatFilter runs at `LOWEST` and can filter/notify before ChatTools cancels at `NORMAL`.
- ChatLayout also installs its renderer at `LOWEST`; the renderer is irrelevant when ChatTools later cancels unless a subsequent plugin uncancels.
- ChatLog runs at `HIGH` with `ignoreCancelled = true`, so ChatTools-cancelled messages are normally not persisted.
- A later plugin can uncancel the event after this listener.
- Because `ignoreCancelled` is false, an event already cancelled by an earlier plugin still reaches ChatTools; when locked, the player can receive the lock message in addition to the earlier plugin's feedback.

The event can be asynchronous. Reading the volatile flag is safe; permission check and Adventure send occur on the event thread. No scheduler handoff is used.

## Scope and bypass boundaries

Only Paper `AsyncChatEvent` is blocked. The lock does not prevent:

- commands;
- private messages handled by ProxyFeatures or other plugins;
- staff chat;
- signs/books/anvils;
- plugin/system broadcasts;
- Discord bridges that bypass Paper chat;
- messages on other backend servers.

A network-wide lock needs a proxy/shared-state design and enforcement in every relevant communication path.

## Messages

| Key | Variables | Use |
|---|---|---|
| `chattools.usage` | none | Missing/unknown subcommand. |
| `chattools.already_locked` | none | Lock requested while already locked. |
| `chattools.not_locked` | none | Unlock requested while unlocked. |
| `chattools.locked_broadcast` | none | Local broadcast after locking. |
| `chattools.unlocked_broadcast` | none | Local broadcast after unlocking. |
| `chattools.cleared_broadcast` | none | Local notice after blank-line clear. |
| `chattools.locked_cant_chat` | none | Sender feedback for cancelled chat. |

No actor/server/reason/duration variables are provided.

## Persistence, database and messaging

ChatTools has no DataProvider registration, database entity, Redis channel, proxy message, API service or PlaceholderAPI expansion.

State and actions are local and transient. There is no moderation history, scheduled unlock, lock reason or responsible actor record.

## Threading and performance

- Lock flag is volatile for async chat visibility.
- Commands execute on the server thread and send messages synchronously.
- Clear performs `onlinePlayers × clear_lines` sends in one command execution. Default 150 with 100 players produces 15,000 empty-message sends plus notices.
- No batching, tick spreading, rate limit or maximum clamp exists.

Use conservative values and avoid clear during peak load.

## Lifecycle

Initialization registers command then listener. Disable has no explicit body; lifecycle cleanup unregisters both. A new enable starts unlocked.

There are no tasks or external resources to drain.

## Developer source map

- Defaults/state/lifecycle: `features/chattools/ChatTools.java`
- Command and broadcasts: `features/chattools/command/ChatCommand.java`
- Lock enforcement: `features/chattools/listener/ChatListener.java`
- Metadata: `features/chattools/meta/Meta.java`

## Operational verification

1. Verify each subcommand permission separately from player and console.
2. Lock twice/unlock twice and verify idempotency messages.
3. Test normal and bypassed players while locked.
4. Confirm commands/private messages/staff chat remain available and document intended scope.
5. Test interaction/order with ChatFilter, ChatLayout, ChatLog and a later plugin that uncancels.
6. Cancel chat before ChatTools and observe possible double feedback while locked.
7. Run clear with 0, negative, default and large values; monitor packet/tick impact.
8. Verify clear does not actually delete signed messages/client logs.
9. Reload/restart while locked and confirm state resets unlocked.
10. Connect to another backend and confirm lock is local.

## Troubleshooting

- **Players still private-message:** expected; only `AsyncChatEvent` is blocked.
- **Other servers remain unlocked:** no Redis/proxy synchronization exists.
- **Blocked message appears in ChatLog:** check listener priorities or another plugin uncancelling after `NORMAL`.
- **Player receives two rejection messages:** ChatTools processes already-cancelled events because `ignoreCancelled` is false.
- **Clear causes lag:** it sends every blank line synchronously; reduce `clear_lines` or implement batched delivery.
- **Chat unlocks after reload:** expected; lock is transient.
- **Tab suggests commands user cannot execute:** completion does not filter permissions.
