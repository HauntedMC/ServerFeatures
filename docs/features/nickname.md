# Nickname

> Paper · Feature name `Nickname` · feature package `features.nickname` · disabled by default

Nickname stores one formatted nickname in DataRegistry's canonical player data, keeps a local UUID→MiniMessage-string cache for fast reads, lets players modify themselves and authorised staff modify known offline/online identities, and exposes `%serverfeatures_nickname%` through PlaceholderAPI.

The feature does **not** directly change Bukkit display name, player-list name, entity custom name, Nametags text or skins. Other surfaces must explicitly consume the DataRegistry nickname or the PlaceholderAPI value.

## Hard integration requirements

Startup requires:

1. connected DataRegistry API;
2. DataRegistry feature flag `NICKNAMES` enabled.

Failure throws `IllegalStateException`; there is no local-file/database fallback inside ServerFeatures. Nickname persistence is owned by DataRegistry's `PlayerData` API, not a ServerFeatures ORM entity.

DataRegistry provides:

- canonical `PlayerIdentity` by UUID/name/identifier;
- `findNickname(playerId)`;
- `saveNickname(playerId,nickname)`;
- `clearNickname(playerId)`.

## Commands and permissions

Actual root: `/nickname`, no `/nick` alias in command metadata.

| Syntax | Permission | Sender | Behaviour |
|---|---|---|---|
| `/nickname <nickname>` | `serverfeatures.feature.nickname.command.nickname` | Player only | Validate and persist own nickname. |
| `/nickname remove` | same | Player only | Clear own nickname. |
| `/nickname <player-or-id> <nickname>` | self permission **and** `serverfeatures.feature.nickname.command.nickname_other` | Player only | Resolve known DataRegistry identity, validate and persist nickname. |
| `/nickname <player-or-id> remove` | both | Player only | Clear target nickname. |

Console receives `general.player_command`; even staff/offline operations are player-only.

The self permission is checked before argument mode, so staff must hold both nodes to modify others.

Missing args sends shared `general.usage`. More than two args sends `nickname.one_word`; nicknames cannot contain spaces because command parsing allows one nickname argument.

Tab completion:

- first argument: current local online player names, regardless of whether caller intends self nickname or has other permission;
- second argument: `remove`;
- no offline/DataRegistry identifier suggestions.

## Complete configuration reference

File: `plugins/ServerFeatures/features/Nickname/config.yml`.

| Key | Default | Exact behaviour and caveats |
|---|---:|---|
| `enabled` | `false` | Requires DataRegistry nicknames, registers join listener/command/PAPI and initializes online players. |
| `minNicknameLength` | `3` | Inclusive minimum of Java `String.length()` on plain rendered text. Direct cast to `int` at handler construction. |
| `maxNicknameLength` | `16` | Inclusive maximum on plain rendered text. Direct cast. |
| `allowedCharacters` | large symbol list | Additional **single UTF-16 char** strings allowed besides `Character.isLetterOrDigit`. Loaded once. |
| `disallowedFormatting` | reset/underline/strikethrough/obfuscated forms | Raw case-sensitive substring blacklist checked before formatting conversion. Loaded once. |

There are no settings for uniqueness, reserved names, profanity, case normalization, spaces, display surfaces, cache TTL, per-server nicknames, prefix/suffix, permission tiers, allowed colours/decorations, update events or database connection.

### Default allowed symbols

The generated list includes decorative symbols such as hearts, stars, musical notes, checks/crosses, geometric shapes, suits, floral/snow symbols, crowns, arrows and related Unicode glyphs. Operators should treat the generated YAML as authoritative and review font/resource-pack support.

Validation iterates UTF-16 `char` values. Supplementary Unicode code points represented by surrogate pairs are tested one half at a time and normally fail unless both surrogate strings were explicitly allowlisted. Combining marks are not letters/digits and need explicit allowance.

### Disallowed formatting matching

Defaults include literal variants:

- reset: `&r`, `§r`, `<reset>`;
- underline: `&n`, `§n`, `<underline>`;
- strikethrough: `&m`, `§m`, `<strikethrough>`;
- obfuscated: `&k`, `§k`, `<obfuscated>`.

Checks use raw `String.contains`:

- case-sensitive (`<RESET>` may bypass if formatter accepts it case-insensitively);
- aliases/closing tags/alternate syntax not listed can bypass;
- text containing these literal sequences is rejected even when intended literally;
- failure message does not inject which detected format despite message template containing `{format}` in defaults.

## Validation and formatting pipeline

`validateNickname(raw)`:

1. reject null/blank as `INVALID_LENGTH`;
2. scan raw text for disallowed formatting strings;
3. convert mixed input with `TextFormatter` to MiniMessage string;
4. convert formatted MiniMessage string to plain text;
5. enforce length;
6. require each plain-text char to be letter/digit or configured allowed symbol;
7. return formatted MiniMessage string for persistence/cache.

There is no explicit restricted feature set during `translateColours`; it only converts mixed input to MiniMessage text. Validation checks plain visible text, not component event/security semantics. When a consumer later deserializes the stored string with permissive features, click/hover/font/etc. syntax that survives conversion and is not blacklisted could be meaningful. Review shared formatter behaviour and restrict accepted tags if nicknames are used in trusted interactive contexts.

Plain text means formatting codes do not count toward min/max length. Colour-only nickname with no visible chars fails min length.

Spaces fail character validation because space is neither letter/digit nor in default allowlist; command with spaces also reaches `one_word` before validation.

### Failure mapping

| Failure | Message |
|---|---|
| `DISALLOWED_FORMATTING` | `nickname.disallowed_formatting` |
| `INVALID_LENGTH` | `nickname.max_length_exceeded` (used for too short, too long, null or blank) |
| `INVALID_CHARACTERS` | `nickname.invalid_characters` |

There is no separate minimum-length message.

## Identity resolution

### Self

Command resolves the player's UUID string through `findIdentityByIdentifier`, not only active cache. This ensures success feedback occurs only with persisted canonical identity.

### Other

Target argument is trimmed and passed to DataRegistry `findIdentityByIdentifier`. It can support usernames/UUID/other identifiers according to DataRegistry's contract, including known offline players.

Unknown target returns `nickname.player_not_found`; resolution exception returns `nickname.data_unavailable`.

No target permission/immunity/vanish check exists. Authorised staff can modify any resolved identity.

## Persistence and command completion

Modern command paths are fully asynchronous:

1. resolve identity;
2. validate synchronously;
3. call DataRegistry save/clear completion stage;
4. update/remove local cache only after persistence succeeds;
5. schedule main-thread feedback through lifecycle task manager;
6. skip feedback when actor is offline.

For target updates, online target receives normal self set/removed message after persistence. Actor receives staff confirmation using canonical `targetIdentity.username()`.

There is no rollback need because cache is changed only after persistence. Failure logs and sends data-unavailable.

`setNickname` stores the formatted MiniMessage string exactly returned by validator; DataRegistry defines column/schema/retention.

## Compatibility live-player methods

`NicknameHandler` also retains synchronous-return compatibility methods:

```java
boolean setNickname(Player,String)
boolean removeNickname(Player)
```

These:

- require active cached DataRegistry identity;
- submit asynchronous persistence;
- return true immediately after submission;
- update cache later;
- log failures but do not report success/failure accurately to caller.

New integrations should use identity-based `CompletionStage` methods so reported success follows persistence.

## Cache lifecycle

`ConcurrentHashMap<UUID,String> nicknameCache` stores formatted nickname string.

### Join/enable load

- `PlayerJoinEvent` calls `initializePlayer`;
- players online at feature enable are also initialized;
- `DataRegistryIdentityGate.runWhenReady` supplies ready canonical identity;
- `findNickname(identity)` asynchronously updates/removes cache.

Listener priority uses its implementation defaults; load completion is asynchronous.

### Failure/empty

Any load error, missing identity or absent nickname removes cache entry. There is no explicit warning in normal load path.

### Quit

No quit listener removes cache. UUID nickname entries remain for the feature instance after players disconnect, supporting offline PlaceholderAPI reads by UUID but creating unbounded-by-time memory proportional to identities loaded/modified. Feature disable/reload discards the handler/map.

### Warm-on-placeholder

When placeholder has no cached value, `warmNicknameIntoCache(OfflinePlayer)`:

1. return cached if present;
2. resolve identity by UUID, active cache first then persisted lookup;
3. query nickname;
4. update/remove cache;
5. return completion.

Placeholder does not wait for this completion.

There is no generation fencing. A slow older load can overwrite/remove cache after a newer command mutation if completions race. DataRegistryIdentityGate handles join readiness but `NicknameHandler` has no per-UUID load/mutation generation.

## PlaceholderAPI

Registered only when PlaceholderAPI plugin is present during feature initialization.

Identifier: `serverfeatures`

Placeholder:

```text
%serverfeatures_nickname%
```

Behaviour:

- unknown parameter: return null;
- null player: empty string;
- cached nickname: return stored MiniMessage string;
- uncached: asynchronously warm cache and immediately return `OfflinePlayer#getName()` or empty when unavailable.

The first read can show real name until a later PAPI evaluation. The returned nickname is the stored formatted string, not explicitly serialized to colour-code text. Whether consuming plugin parses MiniMessage or displays raw tags depends on that plugin/PAPI context.

Warm failures log warning with UUID.

Expansion `persist()` returns true. Feature disable does not store/unregister expansion reference explicitly; PlaceholderAPI/plugin lifecycle and framework cleanup must prevent stale expansion references on reload. This is worth testing.

## Display-surface integration

Nickname itself does not call:

- `Player#displayName`;
- `Player#playerListName`;
- scoreboard teams;
- Nametags manager refresh;
- chat renderer update;
- tablist;
- commands/permissions names.

Consumers must use DataRegistry nickname or `%serverfeatures_nickname%`. Cache updates do not emit a custom nickname-changed event. Surfaces with cached text may remain stale until their own refresh interval/event.

## Messages and variables

| Key | Variables | Use |
|---|---|---|
| `nickname.set` | `{nickname}` formatted string | Self/online target success. |
| `nickname.removed` | none | Self/online target removal. |
| `nickname.one_word` | none | More than two command args. |
| `nickname.disallowed_formatting` | default contains `{format}`, but code does not supply it | Raw blacklist failure. |
| `nickname.max_length_exceeded` | none | Any invalid length/blank. |
| `nickname.invalid_characters` | none | Plain char failure. |
| `nickname.set_other` | `{player}`, `{nickname}` | Staff set confirmation. |
| `nickname.player_not_found` | none | Unknown target. |
| `nickname.other_removed` | `{player}` | Staff removal confirmation. |
| `nickname.data_unavailable` | none | Identity/persistence failure. |

Self permission denial uses shared `general.no_permission_rank` with hard-coded `{rank}` `&6Elite`; staff permission denial uses `general.no_permission`.

## Threading and lifecycle

Identity/nickname calls are asynchronous DataRegistry stages. Player messages and Bukkit target lookup are scheduled back to the feature task manager.

`nicknameCache` is concurrent. Validation is pure except formatter utilities/config snapshots.

Disable is empty:

- listener/command tasks are lifecycle-owned;
- handler/cache becomes unreachable;
- no explicit PAPI unregister;
- no pending-stage generation/cancellation fence inside handler;
- DataRegistry owns persistence resources.

## Persistence and messaging summary

- Persistence owner: DataRegistry `NICKNAMES` feature.
- Identity: canonical `PlayerIdentity.playerId`.
- Server column: none; nickname is network-global.
- ServerFeatures ORM/DataProvider slot: none directly.
- Redis/proxy messaging: none.
- PAPI: `%serverfeatures_nickname%`.
- Java API registration: none; handler methods via feature instance.

## Developer source map

- Requirements/defaults/lifecycle: `features/nickname/Nickname.java`
- Validation/cache/mutations: `features/nickname/internal/NicknameHandler.java`
- DataRegistry adapter: `features/nickname/internal/service/NicknameService.java`
- Command: `features/nickname/command/NickCommand.java`
- Join load: `features/nickname/listener/PlayerJoinListener.java`
- PAPI: `features/nickname/internal/NicknamePlaceholder.java`
- Tests: `src/test/.../features/nickname/`

## Operational verification

1. Start without DataRegistry/NICKNAMES and verify explicit startup failure.
2. Test all permission combinations and exact `/nickname` root.
3. Set/remove self and known offline targets; inspect canonical DataRegistry value.
4. Test min/max exact boundaries, blank, spaces, Unicode BMP/supplementary/combining chars.
5. Test every allowed/disallowed formatting syntax, aliases/case/closing tags and interactive MiniMessage.
6. Verify validation failure message mapping and missing `{format}` injection.
7. Simulate persistence/identity failure and actor disconnect during completion.
8. Race slow join cache load against nickname mutation.
9. Query `%serverfeatures_nickname%` before/after warm and with offline/null player.
10. Consume formatted placeholder in plugins expecting colour-code/plain/MiniMessage.
11. Verify chat/tab/nametags do not automatically update unless configured as consumers.
12. Reload feature and check PAPI expansion duplication/stale references.

## Troubleshooting

- **`/nick` does not exist:** actual root is `/nickname` and no alias is registered.
- **Nickname saved but vanilla name unchanged:** feature only persists/cache/PAPI; it does not mutate Bukkit display surfaces.
- **First placeholder shows real name:** cache warm is asynchronous/non-blocking.
- **Raw MiniMessage tags appear:** PAPI returns stored formatted string; consuming plugin may not parse MiniMessage.
- **Short nickname says “too long”:** all length failures use `max_length_exceeded`.
- **Message shows `{format}` literally:** code does not provide that variable.
- **Unicode emoji rejected despite visual one character:** validation iterates UTF-16 chars and allowed list.
- **Stale cache after rapid join/change:** no per-UUID load-generation fencing exists.
- **Config changes do not apply:** limits/lists are cached at handler construction.
