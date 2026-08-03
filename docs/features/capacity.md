# Capacity

> Paper · Feature ID `Capacity` · disabled by default · Redis-backed PlaceholderAPI view of the authoritative ProxyFeatures Capacity service

## Overview

The ServerFeatures Capacity feature receives complete, versioned snapshots from the authoritative ProxyFeatures Capacity service and exposes them through a local read-only API and the `%capacity_*%` PlaceholderAPI expansion.

Placeholder evaluation never performs Redis or SQL work. The Redis subscriber validates and atomically replaces one in-memory latest-value snapshot. Every placeholder read is therefore constant-time and safe for scoreboards, tab lists, holograms, menus and chat formats.

This feature does not make admission decisions and does not duplicate Capacity configuration. ProxyFeatures remains the only authority for limits, reserved capacity, runtime state and leases.

## Requirements

- ProxyFeatures Capacity enabled and publishing snapshots;
- DataProvider Redis messaging connection `redis`;
- matching `channel` and `publisher_id` on proxy and backend;
- a global `server_name` equal to the exact Velocity backend identifier;
- PlaceholderAPI for `%capacity_*%` values.

PlaceholderAPI is optional for startup. Without it, the local `CapacityAPI` still registers. Without Redis, the feature stays loaded but reports no available snapshot.

## Configuration

```yaml
Capacity:
  enabled: true
  channel: proxy.capacity.snapshot
  stale_after_seconds: 10
  publisher_id: proxy
```

| Key | Default | Meaning |
|---|---:|---|
| `channel` | `proxy.capacity.snapshot` | Redis latest-value snapshot channel. |
| `stale_after_seconds` | `10` | Maximum receive age before the snapshot is invalidated. Must be positive. |
| `publisher_id` | `proxy` | Exact expected authoritative publisher identity. |

The global configuration must also contain:

```yaml
server_name: survival_1
```

This name powers the local-server shorthand placeholders.

## Freshness placeholders

| Placeholder | Value |
|---|---|
| `%capacity_available%` | `true` only while a fresh validated snapshot exists. |
| `%capacity_stale%` | `true` after an accepted snapshot exceeds the freshness window. |
| `%capacity_age_seconds%` | Receive age in whole seconds, or `-1` before the first snapshot. |
| `%capacity_published_at%` | Proxy publication epoch milliseconds, or `0`. |
| `%capacity_active_leases%` | Current authoritative prepared-lease count, or `0`. |

Consumers should use `%capacity_available%` when a stale value must not be displayed as live.

## Scope selectors

The expansion exposes four scope types:

```text
network                     -> proxy hard-limit scope
gameplay                    -> gameplay-global scope
group_<group>               -> configured gamemode/shared group
server                      -> this backend from global server_name
server_<server>             -> exact named backend
```

Examples:

```text
%capacity_network_available%
%capacity_gameplay_capacity%
%capacity_group_survival_state%
%capacity_server_available%
%capacity_server_survival_1_absolute_available%
```

Names are case-insensitive and preserve underscores. Metric suffix parsing uses the complete known suffix, so `survival_1_normal_available` resolves to server `survival_1`.

## Scope metrics

Every scope selector supports:

| Suffix | Meaning |
|---|---|
| `exists` | Whether this scope exists in the fresh snapshot. |
| `capacity` | Configured absolute capacity. |
| `reserved` | Capacity reserved for eligible admissions. |
| `normal_capacity` | `capacity - reserved`. |
| `occupied` | Connected occupancy counted by the scope. |
| `pending` | Prepared admissions not committed yet. |
| `restoration_reserved` | Restart-return reservations. |
| `used` | `occupied + pending + restoration_reserved`. |
| `available` | Alias of `normal_available`; capacity available to ordinary players. |
| `normal_available` | Remaining ordinary-player capacity. |
| `absolute_available` | Remaining capacity including reserved admission headroom. |
| `reserved_available` | Remaining reserved-only headroom. |
| `state` | `open`, `draining`, `closed`, `offline`, or `unavailable`. |
| `open` | Whether the operational state is exactly `OPEN`. |
| `limited` | Whether this numeric scope is enabled (`capacity > 0`). |
| `full` | Whether an enabled ordinary-player limit is exhausted. |
| `absolute_full` | Whether an enabled absolute limit is exhausted. |
| `accepting` | Whether this scope is open and does not block ordinary admission. |

A missing or stale scope returns `0` for numeric metrics, `false` for booleans, and `unavailable` for state. Unknown placeholder syntax returns `null` so PlaceholderAPI can leave it unresolved.

`available` deliberately means ordinary-player capacity. Use `absolute_available` when displaying reserved-capacity headroom to staff. Capacity value `0` disables that numeric scope; in that case `limited=false`, `full=false`, and `accepting` depends only on state. Its finite availability values remain `0` because the scope has no standalone slot ceiling.

## Example scoreboard values

```yaml
line1: '&bNetwerk: &f%capacity_network_used%/%capacity_network_capacity%'
line2: '&bSurvival: &f%capacity_group_survival_used%/%capacity_group_survival_capacity%'
line3: '&bVrij: &f%capacity_group_survival_available%'
line4: '&bStatus: &f%capacity_group_survival_state%'
```

For a public join indicator, combine state and ordinary availability through:

```text
%capacity_group_survival_accepting%
```

## Snapshot validation

The backend accepts a snapshot only when:

- schema version is exactly supported;
- publisher ID matches configuration;
- publisher epoch and sequence are valid;
- sequence increases within one publisher epoch;
- a new publisher epoch is newer than the current fresh snapshot;
- retired epochs cannot become authoritative again;
- proxy and gameplay scopes are present and correctly named;
- group/server keys match their embedded normalized names;
- capacities and counters are non-negative;
- reserved capacity does not exceed absolute capacity;
- state is one of `OPEN`, `DRAINING`, `CLOSED`, `OFFLINE`.

Invalid payload warnings are rate-limited. A fresh accepted snapshot is replaced atomically.

## Lifecycle and failure behavior

- Registration of the local API and PlaceholderAPI expansion happens before Redis subscription.
- Subscription uses DataProvider's logical reconnecting subscription handle.
- Disable unsubscribes asynchronously with a bounded confirmation timeout.
- Late messages after disable are ignored and local state is cleared.
- Snapshot expiry is based on backend receive time, not proxy wall-clock agreement.
- Redis loss does not block the server thread; the last snapshot naturally becomes stale.
- There is no database polling, direct SystemData access, fallback source or compatibility channel.
