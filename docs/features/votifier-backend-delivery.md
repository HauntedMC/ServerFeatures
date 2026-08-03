# Votifier Backend Delivery

> ServerFeatures Votifier 1.1.0 · private durable vote stream consumer

ServerFeatures Votifier consumes exactly one backend-specific durable stream and emits exactly one native tracked `VoteEvent`. There is no shared stream, broadcast mode, source selector, alias, compatibility event, or fallback transport.

## Configuration

```yaml
server_name: survival

Votifier:
  enabled: true
  channel: proxy.votifier.vote
  stream_pattern: "{channel}.{server}"
  consumer_group: ""
```

Resolved stream:

```text
proxy.votifier.vote.survival
```

| Key | Default | Meaning |
|---|---|---|
| `channel` | `proxy.votifier.vote` | Base stream shared with ProxyFeatures. Must not be blank. |
| `stream_pattern` | `{channel}.{server}` | Private-stream template. Must contain `{server}`. |
| `consumer_group` | empty | Explicit group; empty derives `serverfeatures.votifier.<server_name>`. |

The global `server_name` must be the exact registered Velocity identifier, match `[A-Za-z0-9_.-]{1,64}`, and not equal the default `server`. The stream suffix is its lower-case form. Invalid identities fail startup rather than being rewritten.

## Proxy mapping

```yaml
Votifier:
  enabled: true
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

Every listed target must exist in Velocity. Every corresponding backend must use the same exact global `server_name`, base channel, and stream pattern.

## Deployment

Deploy the paired ProxyFeatures and ServerFeatures versions together:

1. assign every reward backend a unique valid `server_name`;
2. list exactly those names in ProxyFeatures `redis.delivery.servers`;
3. configure identical `channel` and `stream_pattern` values;
4. enable ServerFeatures Votifier and VoteReward on each reward backend;
5. restart the proxy and affected backends;
6. verify one live vote and one offline/recovery vote.

There is intentionally no transitional configuration. Missing targets, invalid identities, unavailable Redis, blank channels, or patterns without `{server}` prevent startup.

## Durable processing

For each delivery:

1. DataProvider invokes the private-stream consumer;
2. ServerFeatures schedules one main-thread task;
3. a native `VoteEvent` is dispatched with the immutable processing key;
4. VoteReward registers its completion stage through `event.track(...)`;
5. Redis acknowledgement occurs only after all tracked work succeeds.

A queued proxy replay carries a fresh payload timestamp but the original immutable processing key. Duplicate prevention must use that key.

## Startup logging

```text
Votifier backend="survival", stream="proxy.votifier.vote.survival", consumer_group="serverfeatures.votifier.survival".
```

## Verification

1. Confirm every backend logs its intended private stream.
2. Confirm the stream matches ProxyFeatures derivation exactly.
3. Submit one vote and verify one reward on every configured backend.
4. Verify an unlisted backend receives nothing.
5. Stop one target, submit a vote, restore it, and verify paced replay.
6. Confirm replay uses a fresh timestamp and unchanged processing key.
7. Fail reward processing and confirm acknowledgement is withheld.
8. Confirm invalid identity, channel, pattern, or Redis availability prevents startup.
