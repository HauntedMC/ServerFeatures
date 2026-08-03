# Votifier Backend Delivery

> ServerFeatures Votifier 1.1.0 · private durable vote stream consumer

ServerFeatures Votifier consumes exactly one backend-specific durable stream. There is no shared-stream, legacy, broadcast, compatibility, alias, or mode-selection path.

## Configuration

```yaml
server_name: survival

Votifier:
  enabled: true
  channel: proxy.votifier.vote
  stream_pattern: "{channel}.{server}"
  consumer_group: ""
```

This resolves to:

```text
proxy.votifier.vote.survival
```

### Keys

| Key | Default | Meaning |
|---|---|---|
| `channel` | `proxy.votifier.vote` | Base durable stream name shared with ProxyFeatures configuration. Must not be blank. |
| `stream_pattern` | `{channel}.{server}` | Private-stream template. Must contain `{server}`. |
| `consumer_group` | empty | Explicit durable group. Empty derives `serverfeatures.votifier.<server_name>`. |

The global `server_name` is mandatory, must uniquely identify this Velocity backend, and may not remain the framework default `server`.

## Server-name normalization

Stream normalization is identical to ProxyFeatures:

1. trim;
2. lower-case;
3. replace characters outside `[a-z0-9_.-]` with `_`;
4. collapse repeated underscores;
5. limit to 64 characters.

Examples:

| Global `server_name` | Stream suffix |
|---|---|
| `survival` | `survival` |
| `Survival EU` | `survival_eu` |
| `Survival:EU-2` | `survival_eu-2` |

Blank identities and the default identity `server` fail startup. Invalid or ambiguous configuration is never replaced with another delivery behavior.

## Proxy mapping

ProxyFeatures:

```yaml
redis:
  channel: proxy.votifier.vote
  publish_retry_attempts: 8
  publish_retry_delay_millis: 1000
  delivery:
    servers:
      - survival
      - skyblock
    stream_pattern: "{channel}.{server}"
    max_age_hours: 24
    retry_check_seconds: 15
    replay_delay_millis: 500
    ping_timeout_millis: 2000
    max_pending_per_server: 10000
    max_pending_total: 50000
    queue_file: votifier-delivery-queue.bin
```

ServerFeatures on Survival:

```yaml
server_name: survival

Votifier:
  enabled: true
  channel: proxy.votifier.vote
  stream_pattern: "{channel}.{server}"
  consumer_group: ""
```

ServerFeatures on Skyblock uses `server_name: skyblock` with the same feature configuration.

The base channel, stream pattern, and normalized server identity must match on both sides. ProxyFeatures additionally verifies that every configured target is registered in Velocity before starting its outbox.

## Deployment

Deploy the paired ProxyFeatures and ServerFeatures releases together. Before startup:

1. assign every reward backend a unique global `server_name`;
2. add exactly those Velocity server names to ProxyFeatures `redis.delivery.servers`;
3. configure the same `channel` and `stream_pattern` on both sides;
4. restart the proxy and affected backend features;
5. verify one live vote and one offline/recovery vote.

There is intentionally no transitional configuration. A missing target list, default server identity, unavailable Redis provider, blank channel, or stream pattern without `{server}` prevents startup.

## Durable processing semantics

The existing processing guarantees remain:

- every Redis delivery is moved to the Bukkit main thread;
- native `VoteEvent` or the external Votifier compatibility event is dispatched;
- VoteReward and other aware listeners attach tracked completion stages;
- Redis acknowledgement occurs only after tracked processing succeeds;
- failed processing remains pending for durable redelivery;
- the durable processing key supplied by ProxyFeatures remains the idempotency key.

A replayed queued vote carries a fresh payload timestamp while retaining the original immutable durable processing key. Duplicate protection must use that processing key rather than the refreshed timestamp.

## Startup logging

A successful backend logs the resolved identity, stream, and consumer group:

```text
Votifier backend="survival", stream="proxy.votifier.vote.survival", consumer_group="serverfeatures.votifier.survival".
```

## Verification

1. Confirm every backend logs its intended private stream.
2. Confirm that stream exactly matches the stream derived by ProxyFeatures.
3. Submit one test vote and verify exactly one reward on every configured backend.
4. Verify a backend absent from the ProxyFeatures target list receives nothing.
5. Stop a configured backend, submit a vote, restore it, and verify paced replay.
6. Confirm replay uses a fresh payload timestamp and the original processing key.
7. Confirm invalid server identity, channel, pattern, or Redis availability prevents startup.
