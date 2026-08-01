# ChatLayout

> Paper · Feature name `ChatLayout` · feature package `features.chatlayout` · disabled by default

ChatLayout installs a Paper signed-chat renderer that rebuilds every player message as a server-controlled rank/name prefix plus a permission-filtered user message. It adds configurable literal chat placeholders, mentions with toast notifications, clickable command suggestions, item previews, inventory previews, and signed-message deletion controls for staff.

The renderer preserves Paper's chat event/audience pipeline instead of cancelling and manually rebroadcasting the message, but it does perform substantial per-audience work. This page documents the exact renderer order, configuration contracts, permissions, token lifecycle and current edge cases.

## Event contract and signed-chat ordering

```java
@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
public void onChat(AsyncChatEvent event)
```

The listener does not cancel or send the event. At `LOWEST`, it installs an `event.renderer(...)` callback. A later plugin may replace that renderer; a previously cancelled event is ignored.

For every audience Paper renders to, the callback:

1. calls `renderBaseMessage(sender, audience, message)`;
2. decides whether that viewer may delete the signed message;
3. optionally wraps the **entire rendered component** in a hover and `ClickEvent.callback` that calls `Bukkit.getServer().deleteMessage(event.signedMessage())`.

Because rendering is audience-specific, localization and placeholder output may differ per recipient. It also means token creation, mention replacement and prefix formatting can run once per recipient, not once per original message.

## Complete feature configuration

File: `plugins/ServerFeatures/features/ChatLayout/config.yml`.

| Key | Default | Meaning and lifecycle |
|---|---:|---|
| `enabled` | `false` | Enables renderer/listener and all three Brigadier commands. |
| `mention.enabled` | `true` | Enables `@ExactPlayerName` replacement and mention toasts. Read once into the handler. |
| `mention.cooldown_seconds` | `60` | Cooldown keyed by the **mentioned player**, not sender or sender/target pair. Read once as `Long`. |
| `command_suggest.enabled` | `true` | Enables bracketed command suggestions such as `[/spawn]`. Read once. |
| `item_preview.enabled` | `true` | Enables `[item]` replacement and item-token generation. Read once. |
| `item_preview.token.max_uses` | `100` | Maximum successful token consumptions. Passed directly to `TokenOptions`; `-1` follows TokenService's infinite-use convention. |
| `item_preview.token.expire_seconds` | `300` | Expiry after creation. Negative disables time expiry by passing `null`. |
| `inventory_preview.enabled` | `true` | Enables `[inv]` snapshot links. Read once. |
| `inventory_preview.token.max_uses` | `100` | Inventory token use limit. |
| `inventory_preview.token.expire_seconds` | `300` | Inventory token expiry; negative means no time expiry. |
| `placeholders.<key>` | `ping: "[ping]"` | Ordered mapping from literal token text to a localization key suffix. Blank values are skipped. |

All typed values are read without explicit local fallback in `ChatHandler`; missing/incompatible values may become null/unboxing errors depending on the config service. Reload/re-enable after changes because the handler and placeholder registry snapshot settings at initialization.

## Chat format configuration

Formats are loaded once from:

```text
plugins/ServerFeatures/local/chatformats.yml
```

Expected root:

```yaml
formats:
  admin:
    priority: 10
    prefix: '<red>Admin '</n    name: '%player_name%'
    suffix: '<gray> > '
    prefix_tooltip:
      - '<red>Staff member'
      - '<gray>Stars: <star_tier>'
    name_tooltip:
      - '<yellow>Click to message'
    suffix_tooltip: []
    prefix_click_command: '/profile %player_name%'
    name_click_command: '/msg %player_name% '
    suffix_click_command: ''
```

### Fields

| Field | Default | Current use |
|---|---|---|
| `priority` | `Integer.MAX_VALUE` | TreeMap key and selection order; lower number is checked first. Duplicate priorities overwrite the earlier format. |
| `prefix` | empty | Included after star-tier text. |
| `name` | `%player_name%` | Name section. |
| `suffix` | ` > ` | Appended directly after processed name. |
| `prefix_tooltip` | empty list | Joined with `\n<reset>`, `<star_tier>` replaced with numeric tier. |
| `name_tooltip` | empty list | Joined with `\n<reset>`. |
| `suffix_tooltip` | empty list | Loaded into `ChatFormat` but not used by `buildPrefixComponent`. |
| `prefix_click_command` | empty | Wrapped as `click:run_command` around stars+prefix. |
| `name_click_command` | empty | Wrapped as `click:suggest_command` around name. |
| `suffix_click_command` | empty | Loaded but not used. |

### Format selection

Formats are stored in a `TreeMap<Integer, ChatFormat>`. The first entry in ascending priority order for which the sender has:

```text
chatformat.<format identifier>
```

is selected.

If none match, the **last/highest numeric priority** entry is used as the default, regardless of permission. If no formats load, an in-memory fallback is used:

- identifier `default`;
- priority `Integer.MAX_VALUE`;
- prefix empty;
- name `%player_name%`;
- suffix ` > `.

The fallback does not initialize tooltip/click fields, so empty-registry operation should be tested carefully against null handling in prefix assembly.

## Star-tier permissions

Star text is prepended before the configured prefix. First matching permission wins:

| Permission | Tier | Rendered stars |
|---|---:|---|
| `chatformat.bypass` | 0 | none; checked first |
| `chatformat.d500` | 9 | yellow `✯✯✯` |
| `chatformat.d450` | 8 | yellow `✯✯`, white `✯` |
| `chatformat.d400` | 7 | yellow `✯`, white `✯✯` |
| `chatformat.d350` | 6 | white `✯✯✯` |
| `chatformat.d300` | 5 | white `✯✯`, gold `✯` |
| `chatformat.d250` | 4 | white `✯`, gold `✯✯` |
| `chatformat.d200` | 3 | gold `✯✯✯` |
| `chatformat.d150` | 2 | gold `✯✯` |
| `chatformat.d100` | 1 | gold `✯` |

Tier order is hard-coded from highest donation permission down. The numeric tier is available only in prefix-tooltip replacement as `<star_tier>`.

## User-message formatting permissions

The original Adventure message is first serialized to **plain text**, then reinterpreted as mixed formatting input. This deliberately discards pre-existing click/hover events supplied in the incoming component and reconstructs allowed features based on sender permissions.

Highest applicable level wins:

| Permission | Allowed user features |
|---|---|
| `serverfeatures.feature.chatlayout.format.interactive` | Click, hover, colors, decorations, reset, gradient, rainbow, URL autolinking. |
| `serverfeatures.feature.chatlayout.format.formatting` | Colors, decorations, reset, gradient, rainbow, URL autolinking; no user click/hover. |
| `serverfeatures.feature.chatlayout.format.color` | Colors, reset, gradient, rainbow, URL autolinking; no decorations/click/hover. |
| none | No explicitly enabled formatting features; URL autolinking remains enabled. |

Trusted ChatLayout replacements are injected **after** this permission-gated parsing, so ordinary players can still receive server-created hover/click events for placeholders, commands and previews.

## Renderer transformation order

`renderBaseMessage` executes in this exact order:

1. serialize input message to plain text;
2. parse user formatting according to permissions;
3. replace configured literal placeholders;
4. replace mentions when enabled;
5. replace command suggestions when enabled;
6. create/apply item preview token when enabled;
7. create/apply inventory preview token when enabled;
8. build the sender prefix/name/suffix;
9. append transformed message to prefix.

Order matters. Text introduced by an earlier replacement is represented as components and may or may not be matched by later component replacement depending on its content. Configured placeholder replacement occurs before mentions despite the registry comment suggesting otherwise.

## Configured literal placeholders

`placeholders.<key>: <token>` creates a literal, case-sensitive replacement. Default:

```yaml
placeholders:
  ping: '[ping]'
```

For each token, replacement visible text is:

```text
chatlayout.placeholders.<key>.replacetext
```

built for the **sender** audience. Hover text is:

```text
chatlayout.placeholders.hover
```

built for the **viewer** audience.

Although comments mention combining a description with the hover, `buildReplacement` currently attaches only the global verified-hover component. `chatlayout.placeholders.<key>.description` is used by `/chatplaceholders`, not the chat hover.

The visible localized component can contain shared localization/PAPI output supported by that pipeline; default ping replacement uses `%player_colored_ping%`.

Registry order follows config child order. Duplicate token strings overwrite in the `LinkedHashMap` because the map is keyed by token.

## Mentions

Pattern:

```regex
(?<!\S)@([A-Za-z0-9_]{3,16})\b
```

A mention must begin at the start of text or after whitespace and use a valid 3–16 character Minecraft-style name. Resolution uses `Bukkit.getPlayerExact(name)`, so case/exact-name semantics follow Bukkit.

When online:

- visible mention becomes aqua;
- `handleMention(sender, mentioned)` may show a toast.

Offline/unresolved mentions remain plain text.

### Cooldown semantics

`mentionCooldownMap` is keyed by the mentioned `Player` object and stores the last toast time. A toast is allowed when:

```text
now - lastMention >= mention.cooldown_seconds * 1000
```

This means:

- all senders share one cooldown per mentioned player;
- multiple mentions in one message trigger at most one toast after the first map update;
- renderer execution per viewer invokes mention handling repeatedly, but the first audience render consumes the cooldown and later renders are suppressed;
- self-mentions are allowed;
- there is no quit cleanup or expiration of map keys other than feature instance disposal;
- using Player objects as map keys can retain player references during the feature lifetime.

Toast details:

- title `chatlayout.mention.toast_title` with `{player}` sender name;
- icon `BELL`;
- frame `GOAL`;
- lifetime `40L`;
- UI sound `BLOCK_NOTE_BLOCK_PLING`, volume `0.8`, pitch `2.2`.

## Clickable command suggestions

Pattern:

```regex
\[\s*(/\S[^]]*)\s*]
```

Examples such as `[/spawn]` or `[/msg Player hello]` become:

- white brackets;
- yellow command text;
- hover `chatlayout.command_suggest.hover` localized for viewer;
- `ClickEvent.suggestCommand(cmd)`.

The command must start with `/` followed by a non-whitespace character. Matching stops at the next `]`. There is no permission validation for the suggested command; executing it later follows its normal command permission.

## Item previews (`[item]`)

Raw pattern is case-insensitive `\[item]`.

For each **renderer invocation/audience** when item preview is enabled, before replacement checks complete, the handler:

1. snapshots the sender's current main-hand item (`clone`) or null for air;
2. builds token options;
3. creates a token whose future is already completed with a cloned snapshot;
4. runs component replacement.

Therefore a token is created even when the message contains no `[item]`, and because the renderer is per audience, one chat message can create one item token per viewer. This is a significant lifecycle/capacity detail.

Replacement label:

- non-air: `[<best display name> x<amount>]`;
- air: `[Air x1]`;
- hover `chatlayout.item_preview.hover`;
- click runs hidden command `/__sfip <token>`.

The snapshot is taken during rendering, so different audience render timings could theoretically capture different held items if the sender changes items concurrently.

### Hidden item command

`/__sfip <token>` is a registered Brigadier command with no permission requirement. Console returns result `0`; players consume the token.

Token states:

| State | Behaviour |
|---|---|
| `INVALID`, `EXPIRED` | send `chatlayout.item_preview.expired` |
| `LOADING` | send `chatlayout.item_preview.loading` |
| `EMPTY` | send `chatlayout.item_preview.no_item` |
| `OK` | open shulker preview for shulker-box items, otherwise 3×3 item preview, using `chatlayout.item_preview.title` |

`consumeOnEmpty(true)` means empty payload handling participates in token consumption according to TokenService rules.

Anyone who obtains a valid token can invoke the hidden command; authorization is capability-based by unguessable token, not viewer identity.

## Inventory previews (`[inv]`)

Raw pattern is case-insensitive `\[inv]`.

For every renderer invocation when enabled, the handler immediately creates `InventorySnapshot.from(sender)`, creates a token backed by a completed future, then applies replacement—even when `[inv]` is absent. As with item previews, this can create one unused token per viewer per message.

Visible label is English and hard-coded:

```text
[Alex's inventory]
[Lucas' inventory]
```

Names ending in `s`/`S` use apostrophe only. Brackets are white, label gold, hover uses `chatlayout.inventory_preview.hover`, click runs `/__sfiv <token>`.

### Hidden inventory command

`/__sfiv <token>` has no permission and is player-only by runtime check.

States mirror the item command with inventory-specific messages. On `OK`, `InventoryPreviewAPI.openInventoryPreview` opens the immutable snapshot with title `chatlayout.inventory_preview.title`, injecting `{player}` as the possessive owner name. Note that the default title already appends `Inventory`; with a possessive value this produces the intended form according to localization.

Tokens are capability links and are not bound to the original audience.

## `/chatplaceholders`

Permission:

```text
serverfeatures.feature.chatlayout.command.list
```

The Brigadier root itself requires the permission. Player and console are supported.

The command lists, in order:

1. configured placeholder tokens and descriptions from `chatlayout.placeholders.<key>.description`;
2. `[item]` when item preview was enabled at command construction;
3. `[inv]` when inventory preview was enabled;
4. `[/command]` when command suggestions were enabled.

Feature flags are cached in the command constructor. Config changes require feature reconstruction.

Messages:

- `chatlayout.command.placeholders.empty` when no configured/built-in tokens are enabled;
- `chatlayout.command.placeholders.header`;
- repeated `chatlayout.command.placeholders.entry` with `{pos}`, `{placeholder}`, `{desc}`.

The command serializes localized template/description components to MiniMessage, performs raw string replacement, then deserializes with all default features. Placeholder/description values containing the literal variable names can affect replacement.

## Prefix/name/suffix construction

The selected format is transformed into server-trusted MiniMessage:

- stars + prefix wrapped in `run_command` and hover;
- name wrapped in `suggest_command` and hover;
- suffix appended raw, without its loaded tooltip/click command.

The combined string is run through `TextFormatter` with input `ANY`, preprocesses PlaceholderAPI for the sender, then deserializes with trusted click, hover, color, decoration, reset and gradient features.

Security/format implications:

- config controls trusted click/hover tags;
- click-command values are interpolated into quoted MiniMessage attributes; quotes/special syntax must be escaped correctly in config;
- empty prefix/name commands still create click tags with empty values;
- suffix click/tooltip config is currently dead data;
- PlaceholderAPI is evaluated in prefix/name/suffix/tooltips before component creation;
- prefix layout is sender-specific but rebuilt per viewer.

## Signed-message deletion permissions

| Permission | Viewer can delete |
|---|---|
| `serverfeatures.feature.chatlayout.remove.admin` | Every rendered signed message. |
| `serverfeatures.feature.chatlayout.remove.staff` | Messages from non-staff senders, plus the viewer's own messages. Cannot delete another sender who also has the staff permission. |

Sender staff status is determined by the same `remove.staff` permission.

When deletion is allowed, the entire base component receives:

- hover text hard-coded `Delete Message` in red;
- callback click event invoking `Bukkit.getServer().deleteMessage(signedMessage)`.

This outer hover/click can override hover/click events on the root component depending on Adventure event inheritance, potentially affecting nested placeholder interactions. Test item/command links for staff viewers.

The callback uses Adventure's callback mechanism, not a command permission. Paper's signed-message deletion support/version compatibility is required.

## Threading and side effects

`AsyncChatEvent` and its renderer may execute asynchronously. Renderer work calls numerous Bukkit APIs:

- permissions and exact-player lookup;
- online-state checks;
- inventory/main-hand snapshot;
- full inventory snapshot;
- ToastAPI;
- PlaceholderAPI;
- token creation;
- item metadata display-name resolution.

The code does not explicitly marshal these to the main thread. Validate against the deployed Paper version and integrations. Expensive PAPI expansions also execute once per prefix render/audience.

The handler maps are not fully concurrent (`HashMap<Player,Long>` for mentions). Renderer concurrency should be considered when multiple chat messages render simultaneously.

## Persistence, database and messaging

ChatLayout has no DataProvider/database or Redis/proxy messaging. Preview tokens and mention cooldowns are in-memory local state. Links do not work across backend servers and disappear on feature/server restart.

`TokenService` names:

- `chat.itempreview`
- `chat.inventorypreview`

No public ChatLayout API is registered, though the feature exposes token services through its Java instance.

## Lifecycle

Initialization:

1. create both TokenServices;
2. load `local/chatformats.yml`;
3. load feature-config placeholder registry;
4. construct `ChatHandler` and cache flags/options;
5. register signed-chat listener;
6. register `/chatplaceholders`;
7. register hidden `/__sfip` and `/__sfiv`.

Disable has no explicit cleanup body. Framework lifecycle unregisters listener/commands. Token/cooldown cleanup depends on object disposal and TokenService internals; the feature does not explicitly clear them.

## Developer source map

- Defaults/messages/lifecycle: `features/chatlayout/ChatLayout.java`
- Renderer transformations/token generation: `features/chatlayout/internal/ChatHandler.java`
- Signed-chat/delete controls: `features/chatlayout/listener/SignedChatListener.java`
- Format loading/selection: `features/chatlayout/internal/ChatFormatRegistry.java`
- Format model: `features/chatlayout/internal/ChatFormat.java`
- Configured placeholders: `features/chatlayout/internal/ChatPlaceholderRegistry.java`
- Placeholder list command: `features/chatlayout/command/ChatplaceholdersCommand.java`
- Item command: `features/chatlayout/command/ItemPreviewCommand.java`
- Inventory command: `features/chatlayout/command/InvPreviewCommand.java`
- Star tiers: `features/chatlayout/internal/util/StarTierModifier.java`
- Tests: `src/test/.../features/chatlayout/`

## Operational verification

1. Test format priority, duplicate priorities, permission fallback and empty registry.
2. Verify every formatting permission tier with hostile click/hover MiniMessage input.
3. Test PAPI in prefix/name/tooltips and multiple player languages.
4. Test all star-tier permissions and bypass precedence.
5. Test configured placeholders, list descriptions, case sensitivity and duplicate tokens.
6. Mention online/offline/exact-case/self players, multiple mentions, many viewers and cooldown sharing.
7. Test command suggestions with spaces, malformed brackets and commands the viewer lacks permission for.
8. Test `[item]` air, stacks, named items, NBT, shulkers, expiry and use limits.
9. Test `[inv]` armor/offhand/contents snapshots and owner possessive title.
10. Measure token counts when messages contain no preview tokens and under high viewer counts.
11. Verify tokens cannot be used after expiry/reload and can be shared intentionally by capability.
12. Test staff/admin deletion matrix and nested click/hover behaviour.
13. Test with ChatFilter at the same `LOWEST` priority and later renderer plugins.
14. Run async-thread diagnostics and expensive-placeholder load tests.

## Troubleshooting

- **Format permission appears ignored:** lower numeric priorities are checked first; when none match, highest numeric priority is unconditional fallback.
- **One format vanished:** duplicate `priority` keys overwrite in the TreeMap.
- **Suffix tooltip/click does nothing:** loaded fields are not applied by current prefix builder.
- **Allowed user formatting disappears:** incoming components are serialized to plain text and rebuilt according to format permissions.
- **Many unused preview tokens exist:** item/inventory tokens are created per audience before checking whether the token appears.
- **Mention fires only once across many senders:** cooldown is keyed solely by mentioned player.
- **Preview link works for another player:** tokens are bearer capabilities, not audience-bound.
- **Preview links fail after server switch:** token services are backend-local.
- **Staff cannot click item/command links:** outer delete hover/click may interfere with nested component events.
- **Another plugin's format wins:** it may replace the renderer after this `LOWEST` listener.
- **Async access warnings:** rendering performs Bukkit/PAPI/inventory/toast work without explicit main-thread handoff.
