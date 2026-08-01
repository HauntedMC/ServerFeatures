# PlayerLanguage

> Paper · Feature ID `playerlanguage` · disabled by default · shared DataRegistry language cache

PlayerLanguage loads the language preference stored by DataRegistry, keeps one effective `Language` enum per UUID in memory, and registers `LanguageAPI` so the ServerFeatures localization layer and other features can obtain or update that preference.

It has no backend command. Preference administration is expected to happen through ProxyFeatures or another consumer of the same DataRegistry language model.

## Dependencies and initialization

Initialization requires:

- DataRegistry API present;
- `DataRegistryFeature.LANGUAGE` enabled.

Missing API/support aborts feature initialization.

Startup order:

1. construct `LanguageService` from `dataRegistry.players()`;
2. register pre-login/join/quit listener;
3. register service interface `LanguageAPI` through the feature API manager;
4. call `warm` asynchronously for every player already online.

There is no Redis, ORM or local config-file storage in this feature. DataRegistry is the authoritative persistence layer.

## Configuration

| Key | Default | Meaning |
|---|---:|---|
| `enabled` | `false` | Enables the cache/listener/API. |

No configurable fallback, supported-language list, cache TTL, lookup timeout or code-alias map exists. The fallback is hard-coded to `Language.EN`; accepted values are the enum constants compiled into ServerFeatures.

`getDefaultMessages()` is empty.

## Commands, permissions and placeholders

PlayerLanguage registers:

- no command;
- no permission;
- no PlaceholderAPI expansion;
- no player-facing message;
- no Redis/plugin message.

Access is through `LanguageAPI` and the localization framework.

## Stored language resolution

`warm(UUID)` calls:

```text
PlayerData.findLanguage(playerUuid)
```

When settings are present, resolution order is:

1. `effectiveLanguage`;
2. raw `language`;
3. hard-coded English fallback.

A stored code is accepted only when:

```java
Language.valueOf(code.trim().toUpperCase(Locale.ROOT))
```

succeeds.

Consequences:

- `en` and `EN` resolve to `Language.EN`;
- arbitrary whitespace is tolerated;
- values such as `en_US`, `en-US`, `english` and unsupported locale codes are not normalized/aliased and fall back;
- an invalid effective value can still fall back to a valid raw value;
- blank/missing/error removes the cache entry and returns English.

Lookup exceptions are consumed by `.handle(...)`; `warm` normally completes successfully with the fallback instead of failing to its caller.

## Cache model

`ConcurrentMap<UUID,Language>` stores only non-fallback resolved values. When effective language cannot be resolved, the UUID is removed rather than explicitly storing English.

| Operation | Behavior |
|---|---|
| `get(uuid)` | Cached value or `Language.EN`. Constant-time, no database work. |
| `warm(uuid)` | Asynchronously read DataRegistry, update/remove cache, complete with effective language. |
| `forget(uuid)` | Remove cache entry. |
| `setAsync(uuid,language)` | Persist and update cache only when DataRegistry returns `true`. |
| `set(uuid,language)` | Fire-and-forget wrapper around `setAsync`; caller receives no result/failure. |

There is no TTL or periodic refresh. Changes made by another process become visible on:

- pre-login warm;
- join safeguard warm;
- explicit API `set`/`setAsync`;
- feature reload/online bootstrap.

An online player's preference changed only in ProxyFeatures/database is not automatically pushed to this backend.

## Pre-login ordering

`AsyncPlayerPreLoginEvent`:

- priority `LOWEST`;
- `ignoreCancelled=true`.

The listener calls:

```java
service.warm(uuid).toCompletableFuture().join()
```

This intentionally blocks the asynchronous pre-login worker until DataRegistry lookup/fallback completes so the earliest join UI can read the correct language.

There is no feature-defined timeout. A stalled DataRegistry future can hold a pre-login worker indefinitely. Multiple simultaneous uncached logins can wait concurrently.

Because `warm` converts ordinary lookup failures into English, database errors normally allow login with fallback rather than rejecting it.

## Join and quit ordering

### Join

`PlayerJoinEvent` runs at `MONITOR`, `ignoreCancelled=true` and starts another asynchronous `warm`. It does not wait.

This is a safeguard for identity/settings that may have changed around login, but it creates a late-completion boundary: the join refresh is not generation-fenced or online-checked.

### Quit

`PlayerQuitEvent` uses default `NORMAL` priority and calls `forget`.

A join-refresh future completing after quit can repopulate the cache because `warm` does not check connection generation. The entry then remains until another quit/forget/reload/service disposal. This is a small memory/staleness risk for rapid disconnects or slow DataRegistry calls.

Players already online when the feature enables are warmed asynchronously and can briefly receive English until their lookup completes.

## Write behavior

`setAsync` requires non-null UUID/language and calls:

```text
players.saveLanguage(uuid, language.name(), language.name())
```

Both raw and effective language fields are written to the same enum name.

Only a `Boolean.TRUE` result updates cache. False leaves the previous cached value unchanged. Exceptions:

- log one warning with UUID/root message;
- return `false`;
- leave previous cache unchanged.

`LanguageAPI#set` discards that result. Consumers needing reliable confirmation must use the concrete `LanguageService#setAsync` or a future API extension; the public interface currently exposes only void set.

There is no compare-and-set/version fencing. Concurrent writes complete in persistence order, while cache updates follow future completion order; a slower older write can overwrite a newer cache/database value depending on DataRegistry transaction ordering.

## `LanguageAPI`

Registered interface:

```java
Language get(UUID playerUuid);
void set(UUID playerUuid, Language language);
```

`get` is safe for hot localization paths and never blocks. It is local effective state, not a guaranteed fresh database read.

`set` starts asynchronous persistence and returns immediately. It does not optimistically change cache.

Feature consumers should resolve the service through the feature API/service mechanism rather than retaining the concrete feature across reload.

## Localization integration

PlayerLanguage itself renders no messages. Its purpose is to make language state available to the shared localization handler.

Expected ordering for joining players:

```text
Async pre-login LOWEST warm completes
→ Bukkit join lifecycle/localized features
→ join MONITOR refresh starts
```

Features rendering before the async pre-login event or when PlayerLanguage is disabled use localization fallback behavior.

No locale is inferred from Minecraft client locale; only DataRegistry values are considered.

## Persistence and network behavior

DataRegistry's player-language model is shared persistence. PlayerLanguage does not know which proxy/backend changed it and receives no invalidation event.

There is:

- no local table/entity;
- no Redis subscription;
- no pub/sub acknowledgement;
- no disk cache;
- no offline-player command path.

Cross-server convergence relies on every destination warming at login or explicitly refreshing after a preference change.

## Disable/reload

`disable()` is empty. Listener/API/task resource removal is delegated to the feature lifecycle manager.

The service does not explicitly clear its concurrent cache or mark itself closed. Once the feature/service becomes unreachable, the map is garbage-collectable, but external code retaining the old concrete service can continue reading stale values and late futures can still mutate that old map.

A robust future implementation should add lifecycle generation/closed fencing and explicit clear.

## Important implementation boundaries

- English fallback is hard-coded.
- Supported values are exact `Language` enum names after trim/upper-case.
- No `en_US`/`en-US` canonicalization exists.
- Pre-login blocks without timeout.
- Lookup errors fail open to English.
- Join warm is asynchronous and can complete after quit.
- No TTL/push refresh exists.
- Public `set` is fire-and-forget and cannot report failure.
- Failed/false write leaves prior cache unchanged.
- Concurrent writes have no generation/version ordering.
- Disable does not explicitly clear/close service state.
- No client-locale detection, command, permission or placeholder exists.

## Verification checklist

1. Store valid/invalid values in effective/raw fields and verify resolution order.
2. Test `EN`, `en`, whitespace, `en_US`, `en-US`, blank and unsupported enum values.
3. Stop/slow DataRegistry during pre-login and measure worker blocking/fallback.
4. Enable the feature with already-online players and observe fallback-before-warm behavior.
5. Disconnect immediately after join with a delayed lookup and inspect late cache repopulation.
6. Call API `set` and concrete `setAsync` for true, false and exception results.
7. Race two language writes with controlled completion order.
8. Change language on ProxyFeatures while staying on the backend; confirm no push refresh.
9. Switch backends/relog and confirm destination warm converges.
10. Disable/reload with outstanding futures and retained service references.

## Source map

- Dependency/default/lifecycle: `features/playerlanguage/PlayerLanguage.java`
- Cache, DataRegistry reads/writes and normalization: `features/playerlanguage/service/LanguageService.java`
- Pre-login/join/quit ordering: `features/playerlanguage/listener/LanguageListener.java`
- Registered contract: `features/playerlanguage/api/LanguageAPI.java`
