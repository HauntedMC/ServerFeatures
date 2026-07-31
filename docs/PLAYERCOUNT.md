# PlayerCount feature

`PlayerCount` receives the complete periodic count snapshots published by ProxyFeatures and exposes a
thread-safe local API plus PlaceholderAPI placeholders. Placeholder reads never query Redis and do not
scan Bukkit players; they only read the latest validated immutable snapshot.

## Configuration

`features/PlayerCount/config.yml`:

```yaml
enabled: false
channel: proxy.playercount.snapshot
stale_after_seconds: 10
publisher_id: proxy
```

The channel and publisher ID must match ProxyFeatures. The stale threshold should remain comfortably
above the proxy publish interval. The default 10-second expiry is five times the default 2-second
publication interval.

## PlaceholderAPI

Network totals:

- `%playercount_network_online%` — real online count, including vanished players.
- `%playercount_network_visible%` — online count excluding vanished players.
- `%playercount_network_vanished%` — vanished online count.

Current backend totals, using global `server_name`:

- `%playercount_server_available%` — `true` when the current backend name exists in a fresh snapshot.
- `%playercount_server_online%`
- `%playercount_server_visible%`
- `%playercount_server_vanished%`

Named backend totals:

- `%playercount_server_<server>_available%`
- `%playercount_server_<server>_online%`
- `%playercount_server_<server>_visible%`
- `%playercount_server_<server>_vanished%`

Server names may contain underscores because the suffix is parsed from the right. The per-server
`available` placeholders distinguish a registered zero-player backend from an unknown or mistyped
server name.

Health and diagnostics:

- `%playercount_available%` — `true` only while a fresh validated snapshot exists.
- `%playercount_stale%` — `true` when a received snapshot has expired or the backend clock moved backward.
- `%playercount_age_seconds%` — age since local receipt, or `-1` before receipt or after clock rollback.
- `%playercount_published_at%` — proxy publication epoch milliseconds, or `0` before first receipt.

Count placeholders return `0` when no fresh snapshot exists. Use `%playercount_available%` when a
display needs to distinguish a real zero-player network from unavailable data.

## Validation and ordering

Receivers reject unknown schema versions, unexpected publisher IDs, malformed or impossible counts,
per-server totals exceeding network totals, duplicate normalized server names, duplicate or older
sequences, and delayed messages from a retired proxy epoch. Redis subscriptions use DataProvider's
logical self-healing subscription handle, stop accepting callbacks before shutdown, and unsubscribe
asynchronously so a feature reload or server shutdown cannot block the main thread.
