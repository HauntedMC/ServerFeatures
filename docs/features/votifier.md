# Votifier

> Paper · Feature ID `votifier` · disabled by default · private durable Redis vote consumer

ServerFeatures Votifier is the backend ingress for votes. It consumes the backend's private durable stream, dispatches one native tracked `VoteEvent` on the Bukkit main thread, waits for all registered processing stages, and acknowledges the Redis delivery only after processing succeeds.

ProxyFeatures Votifier is the required trusted producer.

## Commands, permissions and placeholders

Votifier registers no command, permission, PlaceholderAPI expansion, database entity, audit table, or player-facing message.

Redis Streams are the delivery boundary. Downstream features such as VoteReward own reward state and duplicate-processing markers.

## Configuration

```yaml
server_name: survival

Votifier:
  enabled: true
  channel: proxy.votifier.vote
  stream_pattern: "{channel}.{server}"
  consumer_group: ""
```

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `false` | Starts the required Redis provider and private durable subscription. |
| `channel` | `proxy.votifier.vote` | Base stream name shared with ProxyFeatures. Must not be blank. |
| `stream_pattern` | `{channel}.{server}` | Private-stream template. Must contain `{server}`. |
| `consumer_group` | empty | Explicit durable group. Empty derives `serverfeatures.votifier.<server_name>`. |

The global `server_name` is mandatory. It must:

- match `[A-Za-z0-9_.-]{1,64}`;
- equal the registered Velocity backend identifier configured in ProxyFeatures;
- not remain the framework default `server`.

The stream suffix is the lower-case server identifier.

| `server_name` | Resolved stream |
|---|---|
| `survival` | `proxy.votifier.vote.survival` |
| `SkyBlock` | `proxy.votifier.vote.skyblock` |
| `survival_eu-2` | `proxy.votifier.vote.survival_eu-2` |

Names containing spaces, colons, slashes, or other invalid characters fail startup.

## Proxy mapping

ProxyFeatures must list the exact Velocity server name:

```yaml
Votifier:
  enabled: true
  redis:
    channel: proxy.votifier.vote
    delivery:
      servers:
        - survival
      stream_pattern: "{channel}.{server}"
```

Both sides must use the same base channel and stream pattern. ProxyFeatures verifies that each configured target exists in Velocity; ServerFeatures verifies that its local identity follows the same identifier contract.

## Startup

Initialization:

1. initializes feature DataProvider resources;
2. registers Redis messaging provider key `redis`, connection `hauntedmc`;
3. fails when Redis is unavailable;
4. validates channel, pattern, global server identity, and consumer group;
5. derives the private stream;
6. creates `EventBusHandler`;
7. starts one durable subscription;
8. logs backend identity, stream, and group.

A successful startup logs:

```text
Votifier backend="survival", stream="proxy.votifier.vote.survival", consumer_group="serverfeatures.votifier.survival".
```

## Consumer identity

When `consumer_group` is blank, the group is:

```text
serverfeatures.votifier.<lower-case server_name>
```

An explicit group is normalized by:

1. trimming and lower-casing;
2. replacing characters outside `[a-z0-9_.:-]` with `_`;
3. collapsing repeated underscores;
4. truncating to 150 characters.

Every feature instance uses a unique consumer name:

```text
<consumer-group>.<random UUID>
```

Each backend has its own stream. ProxyFeatures performs fan-out by creating one durable event per configured backend stream.

## Wire contract

The subscription filters for:

```text
VoteMessage.TYPE
VoteMessage.class
```

Each delivery provides:

- service name;
- username;
- address;
- vote timestamp;
- immutable durable processing key.

Null service or username is structurally invalid. Such a delivery is logged and acknowledged so it cannot poison the pending queue indefinitely. A null address becomes `-`.

The processing key is mandatory. `VotePayload` rejects a null or blank key instead of deriving a replacement.

## Dispatch and acknowledgement ordering

The Redis callback never calls Bukkit APIs directly.

For each valid delivery:

1. schedule one feature-scoped main-thread task;
2. create `VotePayload` with the durable processing key;
3. synchronously dispatch `VoteEvent` through Bukkit;
4. collect every completion stage registered through `VoteEvent#track`;
5. acknowledge the Redis delivery only after the combined stage succeeds.

If scheduling, event dispatch, or tracked work fails, the event is not acknowledged. Durable pending-entry recovery can redeliver it.

Acknowledgement itself is asynchronous. Ack failure is logged; the durable provider determines subsequent redelivery.

## Native `VoteEvent`

The event payload is created as:

```java
new VotePayload(service, username, address, timestamp, processingKey)
```

`VoteEvent#track(CompletionStage<?>)` combines asynchronous downstream work. Every listener that owns durable business work must register its stage before returning from synchronous event dispatch.

VoteReward does this automatically:

```text
private Redis stream
→ VoteMessage
→ ServerFeatures VoteEvent
→ NativeVoteListener
→ VoteHandler completion stage
→ VoteEvent.track(stage)
→ Redis acknowledgement
```

The event is not cancellable. A listener should complete successfully when a vote is terminally accepted or rejected, and exceptionally when processing must remain pending for retry.

## Processing identity

The durable envelope processing key is the authoritative idempotency key. ProxyFeatures keeps this key unchanged across Redis retries, proxy restarts, backend downtime, and fresh-timestamp replay.

Downstream processing must use this key rather than the payload timestamp. A vote without a durable processing key is invalid.

## Thread model

- Redis callback: DataProvider messaging context.
- Bukkit event creation and dispatch: feature-scoped main-thread task.
- Listener work: listener-defined; asynchronous work must be tracked.
- Completion and acknowledgement: completion-stage context.

## Failure behavior

| Failure | Acknowledged? | Result |
|---|---|---|
| Null service or username | Yes | Invalid delivery is discarded. |
| Missing processing key | No | Payload construction fails and delivery remains pending. |
| Main-thread scheduling failure | No | Logged and left pending. |
| Native event dispatch throws | No | Logged and left pending. |
| Tracked processing fails | No | Logged and left pending. |
| No listener tracks work | Yes | Synchronous event dispatch is terminal. |
| Ack future fails | Not confirmed | Logged; provider may redeliver. |
| Consumer completion fails unexpectedly | — | Severe log. |

A structurally valid event whose listener consistently fails remains pending according to DataProvider's durable recovery behavior. The feature does not dead-letter messages itself.

## Shutdown

On disable:

1. detach the local subscription reference;
2. request `closeAsync()`;
3. wait up to five seconds for confirmed close;
4. restore interrupt status when interrupted;
5. log execution failure or timeout;
6. clear the handler reference.

Already-dispatched business stages are not synchronously awaited after subscription closure. Durable idempotency protects redelivery when shutdown interrupts completion before acknowledgement.

## Security boundary

This backend feature trusts records already present in its private Redis stream. ProxyFeatures owns vote ingress authentication and target selection. Redis access must remain restricted to trusted network components.

## VoteReward integration

```yaml
Votifier:
  enabled: true

VoteReward:
  enabled: true
```

VoteReward listens to the native tracked event and requires Votifier to be enabled.

## Verification checklist

1. Confirm ProxyFeatures lists the exact Velocity server identifier.
2. Confirm local `server_name` matches that identifier.
3. Confirm both sides resolve the same private stream.
4. Submit one vote and trace the durable processing key through VoteEvent and VoteReward.
5. Verify exactly one reward and one acknowledgement.
6. Fail VoteReward processing and confirm the message remains pending.
7. Restore processing and confirm idempotent redelivery.
8. Stop the backend, submit a vote, restore it, and verify ProxyFeatures replay.
9. Confirm replay has a fresh timestamp but unchanged processing key.
10. Disable during an in-flight delivery and verify clean subscription closure and safe redelivery.
11. Verify invalid server identity, channel, pattern, key, or Redis availability prevents successful processing.

## Source map

- Configuration, identity, provider, and subscription startup: `features/votifier/Votifier.java`
- Durable dispatch, acknowledgement, and shutdown: `features/votifier/internal/EventBusHandler.java`
- Native tracked completion contract: `features/votifier/event/VoteEvent.java`
- Strict durable payload: `features/votifier/event/VotePayload.java`
- Shared wire contract: ProxyFeatures contracts `VoteMessage`
