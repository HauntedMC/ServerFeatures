# PlayerCount

> Paper · Feature ID `playercount` · disabled by default · Redis snapshot receiver and local read API

PlayerCount receives complete count snapshots published by ProxyFeatures and exposes the latest validated, fresh immutable snapshot through:

- a registered `PlayerCountAPI` service;
- PlaceholderAPI expansion identifier `playercount`.

Placeholder/API reads never query Redis and never scan Bukkit players. All hot-path reads are local `AtomicReference`/immutable-map operations.

The feature distinguishes:

- **online**: real connected players, including vanished players;
- **vanished**: online players hidden by vanish policy;
- **visible**: `online - vanished`.

## Commands, permissions and messages

PlayerCount registers no command, permission or localization messages. It is a read-only receiver.

## Configuration

```yaml
enabled: false
channel: proxy.playercount.snapshot
stale_after_seconds: 10
publisher_id: proxy
```

| Key | Default | Behavior |
|---|---:|---|
| `enabled` | `false` | Creates store/API/PAPI and attempts Redis subscription. |
| `channel` | `proxy.playercount.snapshot` | Non-durable Redis pub/sub channel. Blank values warn and fall back. Must match ProxyFeatures. |
| `stale_after_seconds` | `10` | Maximum age since **local receipt**. Must be a positive numeric value; otherwise warns and uses 10. |
| `publisher_id` | `proxy` | Exact expected producer identity after trimming. Blank values warn and fall back. |

Global `server_name` identifies the current backend for local-server reads. It is normalized by trim + lower-case. Missing/blank falls back to `server` with a warning.

The stale threshold should remain comfortably above ProxyFeatures' publish interval. With the default 2-second producer interval, 10 seconds tolerates several missed publications.

Configuration is captured when the feature initializes.

## Initialization order and degraded mode

1. Resolve local server name, stale threshold and publisher ID.
2. Construct thread-safe snapshot store.
3. Construct/register `PlayerCountAPI` through the feature API manager.
4. If PlaceholderAPI is present, register persistent expansion `playercount`.
5. Initialize DataProvider.
6. Request Redis `MessagingDataAccess` provider key `redis`, namespace `hauntedmc`.
7. Subscribe to configured channel and message type `playercount_snapshot`.

API and PAPI are registered **before** Redis availability is known. When Redis is unavailable or subscription creation fails:

- feature remains loaded;
- API remains registered;
- placeholders remain registered;
- no snapshot exists;
- availability is false and count placeholders return zero.

Placeholder registration failure—typically identifier collision—logs a warning but does not prevent Redis/API operation.

## Wire contract

The backend decoder intentionally mirrors the stable ProxyFeatures JSON shape without importing the producer implementation class.

```text
type: playercount_snapshot
schemaVersion: 1
publisherId: string
publisherEpoch: string
sequence: positive long
publishedAtEpochMillis: positive long
networkOnline: non-negative int
networkVanished: 0..networkOnline
servers:
  <server name>:
    online: non-negative int
    vanished: 0..online
```

Server names normalize with trim + lower-case only. Underscores, dots, colons and other characters are otherwise preserved by this decoder.

The receiver copies incoming server entries into a new ordered immutable snapshot. It does not retain the producer's mutable map.

## Validation

A message is `INVALID` when any of these conditions applies:

- null message;
- schema version differs from exactly `1`;
- publisher ID null/blank or not exactly equal to configured expected ID after trimming;
- publisher epoch null/blank;
- sequence `<=0`;
- publication timestamp `<=0`;
- local receipt timestamp `<=0`;
- negative network online/vanished;
- network vanished exceeds network online;
- server name normalizes to blank;
- server counts null/negative/impossible;
- two incoming keys normalize to the same server name;
- sum of per-server online exceeds network online;
- sum of per-server vanished exceeds network vanished.

Per-server sums may be **less** than network totals. This supports players on omitted/unregistered servers or producer aggregation choices.

Invalid snapshots do not replace current state. The event bus logs at most one generic invalid warning per 60 seconds. It deliberately does not log the payload or precise reason.

There is no forward-schema compatibility or partial-field fallback: an unknown version is rejected.

## Ordering and proxy epochs

Every accepted snapshot contains a `publisherEpoch` and increasing `sequence`.

### Same epoch

Candidate sequence must be strictly greater than the current sequence. Duplicate and older sequence values are `STALE` and ignored.

### Different epoch

A candidate from a new epoch is rejected when:

- its publication timestamp is less than or equal to the current publication timestamp; and
- the current snapshot is still fresh at receipt time.

When current state is already stale, a different epoch can replace it even with an older publication timestamp.

On accepting a new epoch, the previous epoch is added to a retired-epoch set. Any later delivery from a retired epoch is rejected regardless of sequence/timestamp.

The set retains at most 32 epochs in insertion order. After the 33rd transition, the oldest retired epoch is forgotten; a very old delayed publisher epoch could then be reconsidered under the normal timestamp/freshness rules.

This protects normal proxy restart/failover ordering without unbounded memory.

## Freshness and clock behavior

Freshness uses local receipt time, not producer publication time:

```text
stale when now < receivedAt
or now - receivedAt > stale_after_millis
```

A backend clock moving backward before receipt time invalidates the snapshot. Once a snapshot is marked invalidated, it remains stale even if the clock later catches up, until a new snapshot is applied.

The boundary uses `>` rather than `>=`: a snapshot exactly at the threshold remains fresh for that instant.

Staleness does not remove `current`; it marks it unusable for fresh count/server reads. This allows diagnostics such as publication time and age to remain available.

### Diagnostic semantics

- `available`: current snapshot exists and is fresh.
- `stale`: false before first receipt; true once current snapshot exceeds age or clock moved backward.
- `ageMillis`: `-1` before receipt or when `now < receivedAt`; otherwise elapsed local receipt age, even when beyond stale threshold.
- `publishedAtEpochMillis`: current producer timestamp or `0` before first receipt; it remains available after staleness.

A new valid snapshot clears the invalidated marker.

## Threading and subscription model

PlayerCount uses `MessagingDataAccess.subscribe`, not a durable Redis Stream consumer group. It receives current pub/sub messages and relies on the next periodic ProxyFeatures snapshot after reconnect/reload.

`EventBusHandler` owns one logical `Subscription`:

- double subscription is rejected;
- closed-before/during-subscribe is fenced;
- callbacks check `closed` before applying;
- if closure races after apply, the store is cleared;
- callback directly validates/applies on the messaging callback thread;
- no Bukkit main-thread work occurs.

`PlayerCountSnapshotStore#apply` is synchronized. Current snapshot and invalidated marker use atomics; server/count records are immutable.

DataProvider's logical subscription implementation is responsible for Redis reconnect/self-healing. PlayerCount holds the returned logical handle rather than creating its own retry loop.

## Disable and unsubscribe ordering

Disable:

1. atomically marks event-bus handler closed;
2. detaches subscription reference;
3. starts asynchronous `unsubscribe()`;
4. applies a five-second future timeout and logs when shutdown cannot be confirmed;
5. unregisters PAPI expansion;
6. clears current snapshot, invalidation and retired epochs.

The main server thread does not block waiting five seconds. The timeout is attached asynchronously.

Once closed, incoming callbacks return. A callback racing closure after applying clears the store, preventing late state resurrection after disable.

API manager lifecycle owns service unregistration.

## `PlayerCountAPI`

Registered service class: `PlayerCountAPI`.

| Method | Result |
|---|---|
| `current()` | Latest fresh complete snapshot. |
| `network()` | Fresh network counts. |
| `localServer()` | Fresh counts for normalized global `server_name`. |
| `server(name)` | Fresh counts for normalized name. |
| `isAvailable()` | Fresh complete snapshot exists. |
| `isLocalServerAvailable()` | Fresh snapshot contains current server key. |
| `isServerAvailable(name)` | Fresh snapshot contains named server key. |
| `isStale()` | Current snapshot exists and is stale/invalidated. |
| `ageMillis()` | Receipt age or `-1`. |
| `publishedAtEpochMillis()` | Producer timestamp from current snapshot, even if stale, or `0`. |

Count record:

```java
record Counts(int online, int vanished) {
    int visible() { return online - vanished; }
}
```

Unknown server and unavailable snapshot both produce empty `Optional`; callers should use availability methods when distinguishing real zero from absence.

## PlaceholderAPI

Expansion:

```text
identifier: playercount
version: 1.0.0
persist: true
```

The OfflinePlayer argument is ignored. Parameters are trimmed and lower-cased.

### Network totals

| Placeholder | Value |
|---|---|
| `%playercount_network_online%` | Real network online count. |
| `%playercount_network_visible%` | Network online minus vanished. |
| `%playercount_network_vanished%` | Network vanished count. |

### Current backend

| Placeholder | Value |
|---|---|
| `%playercount_server_available%` | Whether fresh snapshot contains normalized global `server_name`. |
| `%playercount_server_online%` | Current backend real online. |
| `%playercount_server_visible%` | Current backend visible. |
| `%playercount_server_vanished%` | Current backend vanished. |

### Named backend

```text
%playercount_server_<server>_available%
%playercount_server_<server>_online%
%playercount_server_<server>_visible%
%playercount_server_<server>_vanished%
```

Parsing removes prefix `server_` and splits at the **last underscore**. Server names can therefore contain underscores. The suffix must be exactly `available`, `online`, `visible` or `vanished`.

Because all parameters are lower-cased and store lookup normalizes, named lookup is case-insensitive.

Unknown malformed parameters return null to PlaceholderAPI. Valid count parameters return `0` when snapshot/server is unavailable.

### Health/diagnostics

| Placeholder | Behavior |
|---|---|
| `%playercount_available%` | `true` only for fresh snapshot. |
| `%playercount_stale%` | `false` before first receipt; true after expiry/clock invalidation. |
| `%playercount_age_seconds%` | Floor of local age milliseconds / 1000; `-1` before receipt/clock rollback. Can exceed stale threshold. |
| `%playercount_published_at%` | Producer epoch milliseconds from current snapshot; `0` before receipt and retained after stale. |

Use `available`/per-server `available` when zero-versus-unavailable matters.

## No local player scanning

This feature intentionally does not use:

- `Bukkit.getOnlinePlayers()`;
- local VanishAPI;
- Paper events;
- database queries.

The proxy snapshot is authoritative for all network and server counts. This prevents each backend from trying to reconstruct network visibility and keeps placeholder reads constant-time.

Consequently, a wrong/missing producer snapshot is not corrected from local Bukkit state.

## Persistence and delivery guarantees

State is in memory only. Pub/sub snapshots are not replayed after backend startup or Redis reconnect. Availability remains false until the next publication.

This is appropriate for periodic latest-value telemetry: old intermediate counts are not useful, while ordering/epoch validation prevents delayed callbacks from replacing newer state.

There is no MySQL history, disk cache, metric export or command to inspect the snapshot beyond API/placeholders/logs.

## Important implementation boundaries

- Redis is optional at initialization; API/PAPI remain in unavailable mode.
- Channel is non-durable pub/sub.
- Publisher ID comparison is exact after trim and case-sensitive.
- Server names normalize by trim/lower-case only.
- Per-server totals may be lower than network totals.
- Same-epoch sequence must strictly increase.
- Different-epoch acceptance depends on timestamp while current is fresh.
- Only 32 retired epochs are remembered.
- Stale state remains as diagnostic current state.
- Clock rollback permanently invalidates current snapshot until replacement.
- Invalid warnings are generic and rate-limited; stale snapshots are silently ignored.
- Count placeholders return zero on unavailable data.
- No Bukkit/vanish fallback exists.
- Disable unsubscribe is asynchronous and nonblocking.

## Verification checklist

1. Start without Redis/PAPI and confirm API registration/degraded values.
2. Publish valid schema and inspect network/current/named placeholders and API.
3. Test blank/wrong publisher ID, schema, negative/impossible counts and duplicate normalized names.
4. Test per-server sums equal to, below and above network totals.
5. Send duplicate/older/newer sequences in one epoch.
6. Rotate publisher epoch with newer/equal/older publication timestamps while current is fresh and stale.
7. Cycle more than 32 epochs and test a delayed forgotten epoch.
8. Stop publications past the stale threshold and inspect available, stale, age and published-at independently.
9. Simulate backend clock rollback and verify persistent invalidation until a new snapshot.
10. Restart Redis/DataProvider and verify logical resubscription plus next-snapshot recovery.
11. Disable/reload during callback/unsubscribe and confirm no late snapshot resurrection.
12. Test server names containing multiple underscores and malformed placeholder suffixes.
13. Compare proxy snapshot values with local Vanish/Bukkit only as an external validation, not a fallback expectation.

## Source map

- Defaults, degraded initialization, API/PAPI and subscription lifecycle: `features/playercount/PlayerCount.java`
- Validation, ordering, staleness and epoch retirement: `features/playercount/internal/PlayerCountSnapshotStore.java`
- Immutable data model: `features/playercount/internal/PlayerCountSnapshot.java`
- Registered service: `features/playercount/internal/PlayerCountAPI.java`
- Placeholder parser/fallbacks: `features/playercount/internal/PlayerCountPlaceholder.java`
- Logical Redis subscription/close: `features/playercount/internal/messaging/EventBusHandler.java`
- Local wire decoder: `features/playercount/messaging/PlayerCountSnapshotMessage.java`
- Authoritative producer contract: ProxyFeatures PlayerCount contracts/feature
