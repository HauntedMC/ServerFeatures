# Votifier Targeted Delivery

> ServerFeatures Votifier 1.1.0 · per-backend durable vote stream consumer

ServerFeatures Votifier can consume either the historical shared vote stream or a backend-specific stream generated from the global `server_name`. Targeted mode is designed for ProxyFeatures Votifier's persistent per-backend delivery outbox.

## Configuration

```yaml
Votifier:
  enabled: true
  channel: proxy.votifier.vote
  consumer_group: ""
  delivery:
    mode: TARGETED
    stream_pattern: "{channel}.{server}"
```

The global configuration must contain a unique backend identity:

```yaml
server_name: survival
```

Resolved stream:

```text
proxy.votifier.vote.survival
```

### Keys

| Key | Default | Meaning |
|---|---|---|
| `channel` | `proxy.votifier.vote` | Base durable stream name. In legacy mode this is consumed directly. |
| `consumer_group` | empty | Explicit durable group. Empty derives `serverfeatures.votifier.<server_name>`. |
| `delivery.mode` | `LEGACY` | `LEGACY` consumes `channel`; `TARGETED` derives a backend-specific stream. |
| `delivery.stream_pattern` | `{channel}.{server}` | Target stream template. Must contain `{server}` or the default pattern is used. |

`LEGACY` remains the default to avoid silently moving an existing backend away from the historical shared stream during an upgrade. Switch every intended reward backend to `TARGETED` as part of the ProxyFeatures reliable-delivery rollout.

## Server-name normalization

Targeted stream normalization is intentionally identical to ProxyFeatures:

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

The default identity `server` is rejected in targeted mode. Starting with that identity would make multiple backends consume the same target stream and would hide a configuration error.

## Proxy mapping

ProxyFeatures:

```yaml
redis:
  channel: proxy.votifier.vote
  delivery:
    enabled: true
    servers:
      - survival
      - skyblock
    stream_pattern: "{channel}.{server}"
```

ServerFeatures on Survival:

```yaml
server_name: survival

Votifier:
  enabled: true
  channel: proxy.votifier.vote
  delivery:
    mode: TARGETED
    stream_pattern: "{channel}.{server}"
```

ServerFeatures on Skyblock uses `server_name: skyblock` with the same feature configuration.

The base channel and stream pattern must match between ProxyFeatures and ServerFeatures. The configured ProxyFeatures target name must normalize to the same value as the backend's global `server_name`.

## Migration order

A safe rollout avoids a period in which producers and consumers use different streams:

1. deploy the new ServerFeatures code while keeping `delivery.mode: LEGACY`;
2. deploy the new ProxyFeatures code with `redis.delivery.servers` still empty;
3. prepare matching `server_name` values on every backend;
4. change each intended backend to `delivery.mode: TARGETED`;
5. immediately configure the same target list on ProxyFeatures;
6. reload/restart the affected features;
7. send a test vote and verify one reward on every target;
8. take one backend offline, vote, restore it and verify delayed delivery.

For a tightly controlled maintenance window, steps 4–6 should be applied together.

## Compatibility modes

### Legacy

```yaml
delivery:
  mode: LEGACY
```

Consumes `channel` exactly as ServerFeatures did before Votifier 1.1.0. This supports old ProxyFeatures configurations and custom producers.

### Targeted

```yaml
delivery:
  mode: TARGETED
```

Consumes one derived stream for this backend. This is the required mode for ProxyFeatures' per-server persistent outbox.

Aliases `PER_SERVER` and `PER-SERVER` are accepted and normalized to `TARGETED`. Unknown values fail safely to `LEGACY`.

## Durable processing semantics

Only stream selection changes. The existing processing guarantees remain:

- every Redis delivery is moved to the Bukkit main thread;
- native `VoteEvent` or external Votifier compatibility event is dispatched;
- VoteReward and other aware listeners attach tracked completion stages;
- Redis acknowledgement occurs only after tracked processing succeeds;
- failed processing remains pending for durable redelivery;
- the durable processing key supplied by ProxyFeatures remains the idempotency key.

A replayed queued vote can carry a fresh payload timestamp while retaining the original immutable durable processing key. VoteReward should use that processing key—not the timestamp alone—for duplicate protection.

## Startup validation and logging

On startup Votifier logs:

```text
Votifier delivery mode=TARGETED, server_name="survival", stream="proxy.votifier.vote.survival".
```

Targeted startup fails when `server_name` is blank or equal to the default `server`. This is intentional: silently subscribing to a generic target is more dangerous than refusing to start.

An invalid stream pattern that lacks `{server}` falls back to `{channel}.{server}`.

## Verification

1. Confirm every backend logs the intended targeted stream.
2. Compare that stream with the stream published in ProxyFeatures logs/configuration.
3. Verify global `server_name` is unique for every reward backend.
4. Submit one test vote and verify exactly one reward per intended backend.
5. Verify an unrelated backend not listed by ProxyFeatures receives nothing.
6. Stop a backend and confirm its Redis stream receives nothing until ProxyFeatures replays the local queue.
7. Restore the backend and confirm the replay is processed and acknowledged.
8. Confirm the replay uses the original durable processing key despite its refreshed timestamp.
9. Switch one test backend to `LEGACY` and verify it no longer consumes targeted events.
10. Test invalid/default `server_name` and confirm targeted initialization refuses to start.
