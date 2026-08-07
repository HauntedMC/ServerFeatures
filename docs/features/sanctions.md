# Sanctions

> Paper · Feature ID `sanctions` · disabled by default · backend enforcement for shared `MUTE` records

Sanctions is the Paper-side mute enforcer for the shared `player_sanctions` database schema used by ProxyFeatures moderation. It does not create, edit or remove sanctions through commands. Instead it resolves each online player's canonical DataRegistry numeric ID, queries the shared MySQL sanctions table, caches the newest active mute locally, periodically re-queries every tracked online player and cancels Paper chat while that cached mute remains active.

There is no Redis subscription or push invalidation in this feature. A mute added or removed while a player is online becomes visible on the next configured database sweep, unless another local action explicitly refreshes the registry.

## Dependencies and data ownership

Initialization requires:

- DataProvider connection access for `MYSQL` logical connection `player_data_rw`;
- an ORM context capable of mapping ServerFeatures' Paper-side `SanctionEntity` to the shared `player_sanctions` table;
- DataRegistry identity readiness for each player.

The feature calls `initDataProvider(getFeatureName())`, registers connection alias `orm` for `MYSQL/player_data_rw`, and creates an ORM context with `SanctionEntity.class`. Failure to create that context aborts feature initialization.

ProxyFeatures 3.3 deliberately keeps persistence entities out of its shared/public artifacts. ServerFeatures therefore owns a schema-compatible Paper-side mapping:

- `nl.hauntedmc.serverfeatures.features.sanctions.entity.SanctionEntity`;
- `nl.hauntedmc.serverfeatures.features.sanctions.entity.SanctionType`.

The mapping mirrors the shared table columns and enum values used by ProxyFeatures, while the cross-platform dependency on `proxyfeatures-contracts` remains limited to supported messaging contracts. Sanctions is therefore a shared-schema dependency rather than a binary dependency on ProxyFeatures' Velocity runtime implementation classes.

The local mapping assumes the shared schema exposes at least ID, optimistic-lock version, active flag, sanction type, target player ID, target IP, reason, actor fields, created timestamp and expiry timestamp. `SanctionType.MUTE` must continue to match the database enum string written by ProxyFeatures.

## Commands, permissions and placeholders

Sanctions registers:

- no Paper command;
- no permission node;
- no PlaceholderAPI expansion;
- no public feature API/service registration;
- no Redis or plugin-messaging consumer.

All sanction administration belongs to the proxy/shared moderation side. Paper only blocks local `AsyncChatEvent` delivery.

## Configuration

| Key | Default | Behaviour |
|---|---:|---|
| `enabled` | `false` | Enables ORM initialization, listeners, online warm-up and the global refresh task. |
| `muteRefreshSeconds` | `60` | Interval between full online-player database refreshes. Values below `10` are clamped to `10`. |

The configured value is cast through `Number`; a non-numeric config value can fail initialization. The interval is read once during feature initialization. Reloading the file without recreating the feature does not reschedule the existing task.

There is no configurable cache TTL, notification throttle, sanction type list, permission bypass, fail-open/fail-closed switch or Redis channel in the current implementation.

## Player lifecycle and identity ordering

### Players already online during enable/reload

After listener registration, the feature iterates all currently online players and calls the same `restoreMuteState` path used for joins.

### Join

`PlayerJoinEvent` is handled at `MONITOR`. The listener does not set `ignoreCancelled`.

`restoreMuteState` passes the player through `DataRegistryIdentityGate.runWhenReady`. Only after DataRegistry supplies a canonical `PlayerIdentity` does the feature schedule asynchronous work that calls:

```text
registry.trackIfMuted(player UUID, identity.playerId)
```

This ordering avoids querying sanctions by username or creating an alternate identity path. The numeric DataRegistry player ID is the database join key.

A failed readiness callback or asynchronous query is logged. The player remains untracked/unmuted locally until another restore/refresh path succeeds.

### Quit

`PlayerQuitEvent` is handled at `MONITOR`. The UUID is removed from:

- tracked canonical IDs;
- active mute cache;
- feedback throttle state.

Late database completions are fenced by `trackedPlayerIds.computeIfPresent`: state is applied only when the player is still tracked and the stored numeric player ID still equals the query's player ID.

## Registry model

`MuteRegistry` maintains three concurrent maps:

| Map | Purpose |
|---|---|
| `trackedPlayerIds: UUID -> long` | Every online player whose canonical DataRegistry ID has resolved, including currently unmuted players. |
| `muted: UUID -> MuteState` | The newest active mute returned by the database. |
| `lastNotify: UUID -> epoch millis` | Fixed chat-feedback throttle. |

Tracking unmuted players is deliberate: otherwise a mute issued while a player is online would never be found by `refreshAll()`.

`MuteState` contains:

- sanction database ID;
- sanitized reason;
- creation `Instant`;
- nullable expiry `Instant` (`null` means permanent).

`isMuted` is a hot-path cache read. If the cached temporary mute is expired, it removes the cache entry and returns false. Expiry comparison uses `expiresAt.isBefore(Instant.now())`; equality with the current instant is not considered expired by that method until a later instant.

## Database query and expiry mutation

For every tracked player, `SanctionsDataService.findActiveMuteByPlayerId` executes an ORM transaction with this logical query:

```sql
select newest sanction
where active = true
  and type = MUTE
  and targetPlayerId = :playerId
order by createdAt desc
limit 1
```

Consequences:

- only `MUTE` is enforced;
- only the newest active mute is considered;
- multiple active mute rows are not merged;
- bans, warnings, kicks and other sanction types have no Paper enforcement here.

When the selected temporary mute has already expired, the service immediately opens another ORM transaction, finds it by ID, sets `active=false`, and returns no mute. Thus the Paper reader also performs cleanup writes to the shared sanction table.

The expiry check and deactivation are not one ORM transaction: the query transaction completes, then `deactivateById` starts another. A concurrent moderation update can therefore race with this fast-path cleanup; the second transaction checks that the row still exists and is active, but does not revalidate type/expiry.

Database failures from the refresh task are caught at the outer task level and logged. Existing cached mute state remains unchanged because no successful `applyResolvedState` occurs. Operationally this means:

- an already cached mute continues to block chat until its local expiry, even during database outage;
- an unmuted cached player remains unmuted until a later successful refresh;
- a manually removed permanent mute can continue to block chat during an outage.

This is a consequence of cache retention, not a separately configurable outage policy.

## Periodic refresh

The feature schedules one asynchronous repeating task:

- initial delay: `0` seconds;
- period: `max(10, muteRefreshSeconds)` seconds.

Each pass copies the current tracked map entries and runs one ORM query per tracked online player. It is therefore an `O(online players)` query pattern rather than a batch `IN (...)` query.

For each result, `applyResolvedState`:

1. confirms the UUID is still tracked;
2. confirms the tracked numeric player ID still matches;
3. replaces the cache with the resolved sanction or removes the cache entry.

There is no per-player refresh scheduling, Redis invalidation, jitter or query coalescing.

## Chat enforcement and event ordering

`AsyncChatEvent` is handled with:

- priority `HIGH`;
- `ignoreCancelled=true`.

Ordering implications:

- chat cancelled by an earlier listener is ignored by Sanctions;
- Sanctions can cancel before later `HIGHEST`/`MONITOR` chat handlers;
- the handler runs on Paper's asynchronous chat event context.

The chat path performs no database work. It reads the concurrent cache and, when muted, cancels the event immediately.

After cancellation it applies a fixed **1.5-second** per-player feedback throttle. Repeated chat attempts inside that window remain cancelled but receive no additional message.

There is no bypass permission and no distinction between global/local/staff chat surfaces. The feature only observes Paper `AsyncChatEvent`; commands such as `/msg`, plugin-specific chat channels, signs, books and proxy chat are outside this listener unless those systems also route through the same event.

## Temporary and permanent messages

### Permanent mute

Message key: `sanctions.chat_blocked.perm`

Variables:

- `{reason}`.

A sanction is considered permanent when `expiresAt == null`.

### Temporary mute

Message key: `sanctions.chat_blocked.temp`

Variables:

- `{remaining}`;
- `{reason}`.

Remaining time is calculated from whole epoch seconds and rendered using non-zero days, hours and minutes only. Seconds are omitted. Less than one minute renders `0m`; an expiry already reached is also clamped to `0m`.

Examples:

- `2d 3h 4m`;
- `6h 2m`;
- `17m`;
- `0m`.

### Reason sanitization

Reasons are normalized when a database result becomes cache state:

1. null or blank becomes `-`;
2. leading/trailing whitespace is removed;
3. Unicode control characters matching `\p{Cntrl}` are stripped;
4. blank-after-cleanup becomes `-`;
5. length is truncated to at most 512 Java characters.

The value is then passed as a localization replacement. Sanitization does not perform MiniMessage escaping in this service; the localization pipeline's replacement semantics determine whether formatting-like content is interpreted or literal.

## Disable and reload

Feature disable clears all three registry maps. The lifecycle manager owns listener/task/data-provider cleanup.

There is no explicit wait for an in-flight async refresh before `clear()`. Late query application is nevertheless limited by `computeIfPresent`: after `trackedPlayerIds.clear()`, old completions cannot repopulate `muted`.

On re-enable, every online player is warmed through DataRegistry again and the global task starts immediately.

## Persistence and network propagation

Sanctions itself persists nothing beyond mutations to the shared sanction row's `active` flag when an expiry is discovered.

It does **not**:

- subscribe to ProxyFeatures Redis sanction events;
- publish acknowledgements;
- maintain a local sanctions table;
- cache to disk;
- persist notification throttle state;
- receive direct command results.

Propagation latency for an online player is therefore approximately the configured sweep interval plus query/scheduler delay. A joining player gets an immediate asynchronous query once identity is ready.

## Performance and scaling

The hot chat path is constant-time concurrent-map work. Database cost is concentrated in the periodic async sweep.

At `N` tracked online players, one refresh performs up to `N` select transactions, plus extra update transactions for newly observed expired sanctions. With a 60-second default this is manageable for modest populations but should be considered when tuning the interval or database pool.

Because the entire `refreshAll()` loop runs inside one async task, individual queries are sequential unless the ORM/provider internally parallelizes them. A slow query delays refresh of later players.

## Important implementation boundaries

- The page enforces **mutes only**, despite the broader feature name.
- There are no Paper moderation commands.
- There is no permission bypass for staff or console-authored chat.
- Online propagation is polling-based, not Redis-driven.
- The cache retains its last successful state during database failure.
- The newest active row wins; overlapping active mutes are not combined.
- A cached temporary mute self-expires locally even before the next database sweep.
- Database expiry cleanup is performed by Paper in a second transaction.
- Joining players may briefly chat before DataRegistry readiness and the async query complete, because absence from `muted` is treated as not muted.
- A newly issued online mute may chat until the next refresh.
- A removed permanent mute may remain blocked until the next successful refresh.
- Only `AsyncChatEvent` is blocked; command/private/plugin channels require their own enforcement.

## Verification checklist

1. Join with no sanction and confirm the player becomes tracked without entering the mute map.
2. Create a permanent `MUTE` row for an online player and measure detection against `muteRefreshSeconds`.
3. Create a temporary mute and validate `{remaining}` formatting across days/hours/minutes and under one minute.
4. Attempt multiple chat messages inside and outside the 1.5-second feedback throttle.
5. Remove/deactivate a mute while the player stays online and measure release latency.
6. Let a temporary mute expire locally and confirm chat is allowed before/without the next sweep.
7. Confirm the next database query deactivates an expired active row.
8. Add multiple active mute rows and verify the newest `createdAt` row supplies reason/expiry.
9. Stop database access while a muted and unmuted player are cached; verify retained fail-state behaviour.
10. Quit during an in-flight restore/refresh and verify late completion does not recreate state.
11. Test `/msg`, staff chat and other plugin channels separately; do not infer coverage from normal chat.
12. Reload/disable the feature and confirm cache/task/listener cleanup through the lifecycle manager.

## Source map

- Feature defaults, ORM and sweep scheduling: `features/sanctions/Sanctions.java`
- Shared-table persistence mapping: `features/sanctions/entity/SanctionEntity.java` and `SanctionType.java`
- Join/quit/chat ordering: `features/sanctions/listener/MuteListener.java`
- Concurrent online/cache state: `features/sanctions/state/MuteRegistry.java`
- ORM query, expiry deactivation and formatting: `features/sanctions/service/SanctionsDataService.java`
