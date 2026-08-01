# ChatFilter

> Paper · Feature name `ChatFilter` · feature package `features.chatfilter` · disabled by default

ChatFilter inspects the plain-text form of local Paper `AsyncChatEvent` messages and cancels messages that match its disallowed-word, IP-address, non-whitelisted-domain, or similarity-spam rules. It also normalizes excessive capitals for filter evaluation, notifies the sender and authorised local staff, logs the blocked content, and asynchronously sends selected violations to a Discord webhook.

The current implementation is a cancellation filter, not a replacement/sanitization pipeline. Its anti-caps transformation is used internally for subsequent checks and logs, but is not written back to the chat event, so allowed messages retain their original casing.

## Event contract and ordering

```java
@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
public void onPlayerChat(AsyncChatEvent event)
```

At the earliest Bukkit priority:

1. serialize the Adventure message to plain text;
2. call `applyFilters(player, rawMessage)`;
3. cancel the event when the handler returns true.

Consequences:

- messages already cancelled before this listener are ignored;
- later chat listeners see the event as cancelled when a rule matches;
- the filter discards formatting/components during analysis and checks only serialized plain text;
- it does not replace `event.message()` with the anti-caps lowercase result;
- `applyFilters` performs Bukkit player iteration/message sends from an asynchronous chat callback. Paper deployments should verify thread-safety expectations; only Discord HTTP work is explicitly rescheduled asynchronously.

Rule order is strict and first-match wins:

1. bypass permission;
2. anti-caps normalization;
3. disallowed words;
4. IP address;
5. blocked link/domain;
6. spam similarity.

A word violation containing an IP/link is reported only as language use. Spam does not send a Discord webhook in the current implementation.

## Permissions

| Permission | Meaning |
|---|---|
| `serverfeatures.feature.chatfilter.bypass` | Skips every filter, anti-caps normalization, recent-message tracking, notifications, logs and webhook work. |
| `serverfeatures.feature.chatfilter.notify` | Receives local staff notifications for every blocked message. The sender also receives this notification when they hold the permission. |

There are no commands or permission-specific configuration fields.

## Complete configuration reference

File: `plugins/ServerFeatures/features/ChatFilter/config.yml`.

### Anti-caps

| Key | Default | Behaviour |
|---|---:|---|
| `enabled` | `false` | Enables listener registration. |
| `minCapsLength` | `10` | Minimum Java string length before uppercase percentage is calculated. Directly cast to `int` during each message. |
| `maxCapsPercentage` | `20.0` | Maximum allowed percentage of uppercase Unicode code units relative to the entire string length. When exceeded, the working string is converted to lowercase. Directly cast to `double`. |

The denominator includes spaces, punctuation, digits and lowercase characters. The uppercase counter uses `Character.isUpperCase`. Conversion uses Java's default `String#toLowerCase()` without an explicit locale.

Again, this transformation is not applied back to the outgoing chat component; it only affects the following filters, notification/log text, webhook text and spam history.

### Disallowed-word matching

| Key | Default | Behaviour |
|---|---|---|
| `disallowedWords` | bundled list | List loaded once during handler construction through `CastUtils.safeCastToList`. |
| `minPrefixLength` | `2` | For a token ending in a blocked word, the number of extra prefix characters required to allow it. Direct cast to `int` at construction. |
| `minSuffixLength` | `2` | For a token starting with a blocked word, the number of extra suffix characters required to allow it. |

The generated default contains Dutch/English profanity, sexual terms, slurs and common obfuscations. Treat the generated file as the authoritative operational list and review it for community policy and false positives.

Word normalization:

```text
message -> lowercase -> replace every non [a-z0-9] with a space -> split on whitespace
blocked word -> lowercase -> remove every non [a-z0-9]
```

This is ASCII-only. Accented letters and non-Latin scripts become separators/are removed.

For each configured word of length `wlen`, matching has two phases.

#### Token-span matching

The algorithm tries spans from 1 through `wlen` tokens. For each span:

- skip it when any token is longer than the blocked word;
- concatenate the tokens without separators;
- block when the concatenation exactly equals the normalized blocked word.

This catches punctuation/space splitting such as `b a d`, subject to the span loop and token-length condition. Complexity grows with message token count and blocked-word length; keep the list and maximum word lengths reasonable.

An empty/fully non-alphanumeric configured word normalizes to an empty string and can create pathological matching behaviour. Do not configure empty entries.

#### Prefix/suffix matching

For each individual token longer than the blocked word:

- token starts with blocked word: block only when extra suffix length is less than `minSuffixLength`;
- token ends with blocked word: block only when extra prefix length is less than `minPrefixLength`.

This policy deliberately allows longer compounds once sufficient extra characters exist. It does not search for blocked words in the middle of a longer token.

### Domain filtering

| Key | Default | Behaviour |
|---|---|---|
| `whitelistedDomains` | bundled domain list | Loaded once at handler construction. A detected domain is allowed when the matched text contains any configured whitelist string. Matching is case-sensitive in `isWhitelistedDomain`. |

Detected domain regex:

```regex
\b(?:[a-zA-Z0-9-]+\.)+[a-zA-Z]{2,}\b
```

It detects bare domain-style text, including subdomains. It does not consume scheme, port, path or query; matching the domain portion is enough.

Whitelist comparison uses substring containment rather than hostname-boundary validation. For example, a matched domain containing `youtube.com` anywhere is allowed, including a malicious longer domain such as `youtube.com.example.org`. Configure and use this feature with that limitation understood; robust hostname parsing would be safer.

The default whitelist includes common Minecraft/community/media domains and `hauntedmc.nl`.

### IP filtering

No configuration key controls IP filtering.

Regex:

```regex
(?i)\b(?:\d{1,3}\.){3}\d{1,3}\b|(?:[a-f0-9]{1,4}:){7}[a-f0-9]{1,4}\b
```

- IPv4 octets are not range-validated; values above 255 still match.
- IPv6 support covers exactly eight colon-separated groups and not compressed forms such as `::1`.
- Ports are not part of the match, but an address before a port still matches.

### Spam filtering

| Key | Default | Behaviour |
|---|---:|---|
| `maxRecentMessages` | `1` | Maximum remembered accepted messages per UUID. Directly cast to `int` per message. Values `<= 0` can cause `removeFirst()` on an empty list and should not be used. |
| `similarityThreshold` | `0.95` | A new message is spam when similarity to any remembered message is greater than or equal to this value. Direct cast to `double`. |

Messages of length six or fewer skip spam checking and are not added to history.

For longer messages:

```text
similarity = 1 - levenshteinDistance(a,b) / max(a.length,b.length)
```

The normalized anti-caps working message is stored, but other case normalization is not applied unless the anti-caps threshold was exceeded. Punctuation and whitespace remain significant for spam comparison.

When any remembered message crosses the threshold, the new message is blocked and is **not** added to/replacing history. Otherwise the oldest entry is removed when size is at least `maxRecentMessages`, then the new message is appended.

History characteristics:

- concurrent map by UUID, but each value is a plain `ArrayList`;
- no quit cleanup, expiry, time window or maximum player count;
- state persists until feature instance is discarded/reloaded;
- bypassed and short messages do not enter history;
- only accepted messages enter history.

## Discord webhook configuration

| Key | Default | Behaviour |
|---|---|---|
| `discordWebhookURL` | `https://discordhook.url` | Read for every notification. Empty/null logs a warning and skips. The generated placeholder URL should be replaced or cleared before production. |

The webhook task is scheduled through the feature lifecycle's asynchronous task manager for:

- disallowed words: filter type `Taalgebruik`;
- IPs: `Reclame [IP]`;
- blocked links: `Reclame [Link]`.

Spam does not call DiscordService.

The JSON embed includes:

- player name;
- global `server_name` setting;
- filter type;
- blocked working message;
- current ISO-8601 timestamp;
- feature version in footer;
- fixed HauntedMC title/description/icons and red colour.

Dynamic fields are escaped through `JsonUtils.escapeJson`. HTTP transport is delegated to `DiscordUtils.sendPayload`. There is no retry, queue, rate limit, response audit or database fallback in this feature.

## Notifications, logging and variables

The hard-coded Adventure prefix is gray/red `[ChatFilter] ` and is prepended to sender/staff messages.

| Violation | Sender key | Staff key | Staff variables | Log tag | Discord |
|---|---|---|---|---|---|
| Word | `chatfilter.blocked_word` | `chatfilter.notify_blocked_word` | `{name}`, `{message}` | `[FILTERED] ` | yes |
| IP | `chatfilter.blocked_ip` | `chatfilter.notify_blocked_ip` | `{name}`, `{message}` | `[IP FILTERED] ` | yes |
| Link | `chatfilter.blocked_link` | `chatfilter.notify_blocked_link` | `{name}`, `{message}` | `[LINK FILTERED] ` | yes |
| Spam | `chatfilter.blocked_spam` | `chatfilter.notify_blocked_spam` | `{name}`, `{message}` | `[SPAM] ` | no |

Staff notification iterates only local `Bukkit.getOnlinePlayers()` and filters the notify permission. A code TODO explicitly notes cross-server delivery is not implemented.

Logs include the player's current name and full blocked working message. Treat server logs and Discord as potentially sensitive moderation data and apply appropriate retention/access controls.

## Interaction with ChatLayout and signed chat

ChatFilter serializes the current event component to plain text at `LOWEST` and only cancels; it does not alter the component or renderer. If it permits the message, later ChatLayout/other listeners continue with the original component.

Because it works at `LOWEST`, later plugins can uncancel the event or replace the message after filtering. ServerFeatures does not enforce a final monitor-stage cancellation here.

Direct plugin messages such as `/broadcast chat`, system messages, private messages and proxy chat do not fire this `AsyncChatEvent` path and are not filtered.

## Threading and lifecycle

`AsyncChatEvent` can run asynchronously. The handler performs:

- config reads;
- concurrent-map access and mutable list operations;
- player permission checks;
- Adventure messages to sender;
- iteration of Bukkit online players and staff message sends;
- feature logging;
- async task scheduling.

The implementation does not reschedule sender/staff Bukkit operations to the main thread. Paper currently supports some Adventure message sends from async contexts, but `Bukkit.getOnlinePlayers()` and surrounding plugin compatibility should be tested. A safer future design would keep pure analysis async and marshal Bukkit audience notification to the server scheduler.

Disable has no explicit cleanup. Lifecycle cleanup unregisters the listener and cancels feature-owned Discord tasks that have not run; the handler and recent-message map become unreachable. There is no in-flight HTTP drain guarantee documented by this feature.

Config lists/prefix-suffix values are loaded at construction. Anti-caps, spam and webhook/global-server settings are read during message processing. Apply list changes through a feature reload.

## Persistence, database and messaging

ChatFilter uses no DataProvider database entity and stores no moderation record beyond logger output/Discord webhook. It publishes no Redis messages and has no proxy integration.

A blocked message is not durable if logging/webhook delivery fails. For auditable sanctions/moderation, integrate an explicit database event/log contract.

## Developer source map

- Defaults/messages/lifecycle: `features/chatfilter/ChatFilter.java`
- Rule engine/state/notifications: `features/chatfilter/internal/ChatHandler.java`
- Paper chat event bridge: `features/chatfilter/listener/ChatListener.java`
- Discord embed/transport: `features/chatfilter/internal/services/DiscordService.java`
- Metadata: `features/chatfilter/meta/Meta.java`

## Operational verification

1. Verify bypass and local notify permissions.
2. Test each rule independently and combined to confirm first-match order.
3. Test anti-caps above/below length and percentage boundaries; confirm allowed outgoing text retains original case.
4. Test punctuation/space splitting of blocked words, prefixes/suffixes at exact thresholds, compounds and middle-of-token cases.
5. Test accented/non-Latin text and review ASCII normalization false positives/negatives.
6. Test valid/invalid IPv4, compressed/full IPv6, hostnames, subdomains, uppercase domains and whitelist-substring bypasses.
7. Test spam at exact threshold, short messages, history size >1, reconnect and reload.
8. Verify sender/staff messages, full logs and webhook payloads contain the normalized working text.
9. Confirm spam intentionally has no webhook.
10. Test Discord outage/invalid URL and ensure chat processing is not blocked by HTTP.
11. Test with ChatLayout, chat logging and plugins at later priorities, including a plugin that uncancels/replaces messages.
12. Load-test long tokenized messages against a large blocked-word list and monitor async chat latency.

## Troubleshooting

- **Caps are not changed in visible chat:** expected; lowercase is only the internal filter input.
- **Whitelisted-looking malicious domain passes:** whitelist matching is substring-based, not hostname-boundary parsing.
- **Compressed IPv6 is not blocked:** the regex only supports eight explicit groups.
- **Spam history survives reconnect:** there is no quit cleanup; it lasts for the feature instance.
- **Config list edits do not apply:** lists and prefix/suffix values load at handler construction; reload the feature.
- **Discord receives no spam notices:** current code sends only word/IP/link violations.
- **Staff on other servers see nothing:** notifications are backend-local; cross-server TODO is unimplemented.
- **Another plugin still broadcasts a blocked message:** later listeners may uncancel, or the message may not use `AsyncChatEvent`.
- **Async-thread warnings occur:** the handler performs Bukkit audience operations inside `AsyncChatEvent`; consider rescheduling notification work.
