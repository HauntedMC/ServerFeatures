# StaffChat

> Paper · Feature ID `staffchat` · disabled by default · prefix-to-Redis publisher

Paper StaffChat intercepts ordinary `AsyncChatEvent` messages that begin with one of three configured prefixes and publishes a shared `StaffChatMessage` to Redis channel `proxy.staffchat.message`. ProxyFeatures is expected to consume, format and distribute that message across the network.

This backend feature is **send-only**. It does not subscribe to Redis, receive network staff chat, format incoming messages or deliver them to Paper audiences.

## Dependencies and initialization

Initialization:

1. initializes the feature's DataProvider resources;
2. requests non-durable Redis `MessagingDataAccess` with provider key `redis` and namespace `hauntedmc`;
3. aborts feature initialization when messaging is unavailable;
4. creates the publish-only `EventBusHandler`;
5. constructs the three-channel prefix registry;
6. registers the async chat listener.

The wire payload class comes from ProxyFeatures contracts:

```text
nl.hauntedmc.proxyfeatures.contracts.messaging.StaffChatMessage
```

Paper and ProxyFeatures must therefore deploy compatible contract versions and Redis serialization settings.

## Configuration

| Key | Default | Channel ID | Permission |
|---|---|---|---|
| `enabled` | `false` | — | — |
| `staff_prefix` | `!` | `staff` | `proxyfeatures.feature.staffchat.staff` |
| `team_prefix` | `@` | `team` | `proxyfeatures.feature.staffchat.team` |
| `admin_prefix` | `#` | `admin` | `proxyfeatures.feature.staffchat.admin` |

Prefix values are directly cast to `String` during handler construction. Null, blank IDs or blank/null prefixes are skipped; wrong non-string config types can fail initialization.

There is no configurable Redis channel, provider namespace, permission template, format, receive subscription or source-server fallback in the feature config.

### Prefix collisions and ordering

Channels are stored in a map keyed by prefix. If two configured channels use exactly the same prefix, the later registration overwrites the earlier one. Registration order is:

1. staff;
2. team;
3. admin.

Thus identical prefixes resolve to the admin channel last.

For overlapping prefixes, the handler chooses the **longest configured prefix** that matches the start of the plain message. Example: with `!` and `!!`, `!!hello` resolves to `!!`.

Prefix matching is exact and case-sensitive.

## Permissions

The channel object constructs permissions as:

```text
proxyfeatures.feature.staffchat.<channel-id>
```

Despite running in ServerFeatures, these are ProxyFeatures-namespaced nodes:

- `proxyfeatures.feature.staffchat.staff`;
- `proxyfeatures.feature.staffchat.team`;
- `proxyfeatures.feature.staffchat.admin`.

There is no ServerFeatures alias, base permission, bypass or receive permission in this module.

A critical behavior: when a message starts with a configured prefix but the sender lacks that channel's permission, the listener simply returns. It does **not** cancel the chat or warn the sender. The original prefixed text therefore continues through normal public chat unless another feature/plugin handles it.

## Chat interception and event ordering

`AsyncChatEvent` is handled at `HIGH` with `ignoreCancelled=true`.

Processing order:

1. Serialize `event.message()` to plain text with `ComponentFormatter`.
2. Resolve the longest matching configured prefix.
3. Return if no channel matches.
4. Check the channel permission.
5. Return without cancellation when unauthorized.
6. Remove the prefix from the plain string and trim surrounding whitespace.
7. Create `StaffChatMessage(prefix, message, playerName, serverName)`.
8. Cancel the Paper chat event.
9. Asynchronously publish to Redis channel `proxy.staffchat.message`.

Because the event runs asynchronously, component serialization and the `redisBus.publish` call are initiated from the async chat context. No Bukkit audience iteration or main-thread scheduling occurs in this feature.

### Interaction with other chat features

At `HIGH`/ignore-cancelled:

- an earlier listener that cancels the message prevents StaffChat publication;
- StaffChat cancels authorized prefixed messages before later listeners;
- later handlers using `ignoreCancelled=true` skip them;
- later handlers that process cancelled events can still inspect/modify them;
- ChatFilter, ChatLayout, ChatLog and other listeners must be reviewed by priority to determine whether staff messages are moderated/logged before cancellation.

The feature serializes the current message component to **plain text**, so Adventure formatting, click/hover events, MiniMessage tags already parsed into styling and signed-chat decorations are not included in the Redis message.

A message consisting only of the prefix produces an empty trimmed channel message and is still published; there is no empty-content rejection.

## Wire message

Paper constructs `StaffChatMessage` with:

| Field | Source |
|---|---|
| channel/prefix field | The configured matching prefix, not the internal channel ID. |
| message | Plain serialized chat after prefix removal and trim. |
| sender | Current Paper player name. |
| server | Global config `server_name`, directly cast to `String` when the listener is constructed. |

The channel ID (`staff`, `team`, `admin`) and permission are not explicitly passed by this class; ProxyFeatures must map/interpret the prefix consistently with its own configuration/contracts.

`server_name` is captured once at listener construction. Later global-config changes do not update the value until feature reconstruction. Missing/wrong type can yield null or a cast failure depending on the config service result.

## Redis publication semantics

Hard-coded Redis channel:

```text
proxy.staffchat.message
```

`MessagingDataAccess.publish(channel, message)` returns a future. The listener cancels public chat before publication completes.

On publication failure:

- a generic severe log `Failed to publish staffchat message.` is emitted;
- exception details/root cause are not included by `EventBusHandler`;
- the sender receives no failure message;
- the original public chat remains cancelled;
- there is no retry, durable stream, outbox or local fallback.

This is at-most-attempted transient pub/sub behavior. A Redis outage or subscriber disconnect can lose the message.

There is no message ID, acknowledgement, deduplication, ordering key or persistence added by Paper StaffChat beyond whatever the shared message/provider supplies internally.

## Receive behavior

This feature registers no `subscribe` call and no Redis callback. It does not:

- display messages published by ProxyFeatures or another backend;
- filter recipients by permissions;
- render channel colors/format;
- play sounds;
- send Discord messages;
- maintain toggle mode.

Any incoming display is wholly ProxyFeatures or another module's responsibility. The old assumption that ServerFeatures “receives and displays” staff chat is not supported by the implementation.

## Commands and channel modes

There is no `/staffchat`, `/sc`, `/teamchat`, `/adminchat` or toggle command on Paper. Only prefix input is supported.

Persistent/toggled staff chat mode, if offered to users, must be owned by ProxyFeatures or another plugin. Paper StaffChat does not query such state.

## Localization and placeholders

`getDefaultMessages()` is empty. ServerFeatures StaffChat sends no player-facing message itself—not even permission denial or Redis failure.

No PlaceholderAPI expansion is registered. Message formatting happens downstream on the consumer side.

## Lifecycle and cleanup

`disable()` is empty. Resource cleanup relies on the lifecycle/data manager to release Redis provider resources and unregister the listener.

`EventBusHandler` has no closed flag or generation fencing. Futures completing after feature disable can still execute their `exceptionally` logger callback. There are no inbound callbacks to fence.

The channel registry is immutable in practice after construction; prefix config changes require feature reconstruction.

## Performance and thread model

For each chat event:

- one Adventure-to-plain serialization;
- up to three prefix comparisons and longest-match selection;
- one permission check;
- one transient Redis publish for authorized matches.

Channel resolution currently streams the small map and is effectively constant cost. No database work occurs.

The feature does not block waiting for Redis publication. Backpressure/failure behavior belongs to DataProvider's messaging implementation.

## Security and privacy boundaries

- Unauthorized prefix use is not kept private; it can appear in public chat.
- Plain staff messages, player names and backend server names leave the Paper process over Redis.
- Paper performs no content moderation or escaping specific to staff chat after plain serialization.
- Channel authorization is evaluated only at send time on Paper.
- Recipient authorization must be enforced by ProxyFeatures; Paper sends no recipient list.
- Prefix values are part of the payload, so mismatched proxy/backend prefix configuration can misroute or fail to categorize messages.

## Important implementation boundaries

- Send-only; no subscription.
- Non-durable pub/sub; no retry/acknowledgement.
- Hard-coded Redis provider/channel.
- Permissions use the `proxyfeatures` namespace.
- Unauthorized prefixed text remains normal chat.
- Authorized chat is cancelled before publish success is known.
- Content is plain text; component styling/events are discarded.
- Empty messages after prefix stripping are allowed.
- Prefixes are captured on initialization.
- Exact duplicate prefixes overwrite by staff→team→admin registration order.
- Longest overlapping prefix wins.
- No player feedback or default localization messages.
- No commands or toggle state.

## Verification checklist

1. Send each default prefix with its exact permission and inspect the serialized Redis payload.
2. Remove permission and confirm whether the prefixed message becomes public as documented.
3. Configure identical and overlapping prefixes and verify overwrite/longest-match behavior.
4. Send only a prefix and confirm an empty message is published.
5. Send styled/interactive Adventure chat and inspect the plain payload.
6. Cancel chat at priorities below HIGH and confirm StaffChat skips it.
7. Inspect ChatFilter/ChatLog/ChatLayout priority interactions to determine moderation/audit coverage.
8. Stop Redis during send and verify public cancellation, generic severe log and no sender feedback.
9. Disconnect/restart the proxy subscriber and confirm transient messages are not replayed.
10. Change `server_name`/prefix config without feature recreation and verify captured values remain.
11. Verify ProxyFeatures enforces recipient permissions independently.
12. Disable/reload while publish futures are outstanding and inspect lifecycle cleanup/log callbacks.

## Source map

- Defaults and Redis initialization: `features/staffchat/StaffChat.java`
- Prefix/channel permission model: `features/staffchat/internal/ChatChannel.java`
- Collision/longest-prefix registry: `features/staffchat/internal/ChatChannelHandler.java`
- Async chat ordering and payload creation: `features/staffchat/listener/ChatListener.java`
- Publish/failure behavior: `features/staffchat/internal/messaging/EventBusHandler.java`
- Shared payload: ProxyFeatures contracts `StaffChatMessage`
