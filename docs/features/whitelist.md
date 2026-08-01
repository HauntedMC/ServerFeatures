# Whitelist

> Paper · Feature ID `whitelist` · disabled by default · pre-login permission/OP gate

Whitelist is a backend maintenance gate. When enabled, every login is rejected during `AsyncPlayerPreLoginEvent` unless either:

- LuckPerms grants `serverfeatures.feature.whitelist.bypass`; or
- Bukkit reports the UUID as an operator.

It does not use Bukkit's native whitelist, DataRegistry, a configured username/UUID list or group-name configuration.

## Commands, permissions and placeholders

No command or PlaceholderAPI expansion is registered.

| Permission | Effect |
|---|---|
| `serverfeatures.feature.whitelist.bypass` | Allows login when LuckPerms resolves the node to true. |

The node is checked directly through LuckPerms API. Bukkit's ordinary permission attachment calculation for an online Player cannot be used because the Player has not joined yet.

There is no separate administration, status, list, reload or bypass-other command.

## Configuration

| Key | Default | Meaning |
|---|---:|---|
| `enabled` | `false` | Registers the asynchronous pre-login gate. |

There are no configuration keys for allowed identities, groups, time windows, failure policy, timeout, OP fallback, proxy synchronization or native-whitelist integration.

Rejection text is localization key `whitelist.kick_message`.

## Admission event and ordering

`AsyncPlayerPreLoginEvent` is handled at:

- priority `HIGHEST`;
- `ignoreCancelled=true`.

Already-disallowed logins are left unchanged. Otherwise the listener:

1. reads the login UUID;
2. tries LuckPerms bypass resolution;
3. when that returns false, obtains `Bukkit.getOfflinePlayer(uuid)` and checks `isOp()`;
4. returns when either bypass path succeeds;
5. builds the kick component;
6. calls `event.disallow(KICK_WHITELIST, component)`.

The check occurs before the full join phase, avoiding a later join kick/reconfiguration experience.

Another listener after HIGHEST cannot use a higher standard Bukkit priority, but registration order among HIGHEST handlers and listeners that deliberately alter results at MONITOR/nonstandard behavior should still be tested.

## LuckPerms integration

LuckPerms is used only when plugin name `LuckPerms` exists in the plugin manager.

The method then:

1. obtains `LuckPermsProvider.get()`;
2. calls `UserManager#loadUser(uuid).join()`;
3. obtains static query options from the context manager;
4. checks the bypass node in cached permission data.

### Context semantics

The feature uses **static query options**, not a query context containing backend/server/world information. Context-specific assignments that require server/world keys may therefore not apply unless LuckPerms includes them in static options.

### Failure policy

Every throwable in LuckPerms lookup is swallowed and treated as no permission bypass. The listener then still tries the OP fallback.

This is fail-closed for non-OP users. During LuckPerms outage, load failure or API incompatibility, only operators can enter.

### Blocking behavior

The event itself is asynchronous, so the code intentionally waits with `.join()`. There is no feature-defined timeout. A slow/stalled LuckPerms storage load can hold the Paper pre-login worker for an unbounded period and consume login capacity.

Multiple simultaneous uncached logins can trigger multiple concurrent user loads.

## OP fallback

Whenever LuckPerms does not grant bypass—including normal false results—the listener calls:

```java
Bukkit.getOfflinePlayer(uuid).isOp()
```

This means OP always bypasses regardless of LuckPerms denial.

The fallback is hard-coded and cannot be disabled. Offline-player lookup is performed from the async pre-login event; compatibility/thread-safety and potential disk/profile access depend on the supported Paper implementation.

The feature checks UUID OP state, not player-name text.

## Kick message rendering

The message is built without `.forAudience(...)` because no Player audience exists yet. Therefore:

- player language cannot be selected through an online Player object;
- player-specific PlaceholderAPI values are unavailable;
- default/global localization context is used;
- no explicit variables are supplied.

The result code is `AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST`, but it is independent of Bukkit's native `white-list` setting and `whitelist.json`.

## Native whitelist relationship

This feature never calls:

- `Server#setWhitelist`;
- `OfflinePlayer#setWhitelisted`;
- `OfflinePlayer#isWhitelisted`;
- native `/whitelist` commands;
- `whitelist.json`.

Native whitelist state can independently reject players before/alongside this listener. Conversely, adding a player to native whitelist does not satisfy this feature's gate.

The Sanitize feature currently forces native `white-list=false` and empties `whitelist.json` when its relevant tasks are enabled. That makes this permission/OP gate the intended backend lock in such deployments, but the features are not directly coupled by API.

## Proxy/network behavior

Whitelist is local to one Paper backend. It does not publish maintenance state or ask ProxyFeatures to prevent routing.

A proxy can still attempt to connect an unauthorized player to the backend, after which Paper rejects pre-login. For a smooth network experience, proxy admission/server-selection rules must be configured separately.

There is no Redis cache, cross-server allowlist or global maintenance toggle.

## Lifecycle

Initialization only registers the listener. `disable()` is empty; lifecycle manager unregisters it.

There is no state to clear and no in-flight lookup generation/closed flag. A pre-login event already executing during disable can continue its LuckPerms wait and decision according to Bukkit listener execution lifecycle.

Changing LuckPerms permissions affects subsequent logins without feature reload because user data is loaded per event.

## Security boundaries

- Forwarded UUID correctness is essential. The gate trusts `AsyncPlayerPreLoginEvent#getUniqueId()`.
- In offline/proxy mode, misconfigured Velocity forwarding can produce incorrect UUIDs and unexpected OP/bypass behavior.
- OP is an unconditional fallback bypass.
- LuckPerms errors are not logged by this listener, reducing diagnostic visibility.
- There is no rate limiting or timeout.
- No username allowlist exists despite the feature name.

## Important implementation boundaries

- Enabling the feature denies everyone except LP-bypass or OP.
- No native whitelist entries are consulted.
- No DataRegistry/database is used.
- LuckPerms query uses static context.
- LuckPerms loading blocks the async pre-login worker without timeout.
- LuckPerms failures silently fall back to OP-only admission.
- OP fallback cannot be disabled.
- Kick localization has no player audience.
- Proxy routing is not changed.
- There are no commands or runtime status APIs.

## Verification checklist

1. Enable with a normal non-OP player and confirm `KICK_WHITELIST` rejection.
2. Grant/revoke the exact LuckPerms node and retry without restarting.
3. Test context-specific LuckPerms assignments versus static query options.
4. Test OP with explicit LuckPerms false/undefined and LuckPerms unavailable.
5. Stop/slow LuckPerms storage and measure pre-login worker blocking/capacity.
6. Test many simultaneous uncached UUID logins.
7. Enable native Bukkit whitelist independently and test interaction with both gates.
8. Run behind Velocity with correct and intentionally test-environment-misconfigured forwarding to validate UUID assumptions.
9. Change localization language/placeholders and verify no player-specific audience exists.
10. Disable/reload while pre-login lookups are active.
11. Verify proxy sends a clear destination/maintenance message separately.

## Source map

- Feature/default message/lifecycle: `features/whitelist/Whitelist.java`
- Async admission, LuckPerms and OP fallback: `features/whitelist/listener/PlayerLoginListener.java`
