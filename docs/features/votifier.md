# Votifier

> Paper · Feature ID `votifier` · disabled by default · durable Redis vote consumer

ServerFeatures Votifier does not open a Votifier TCP port and does not receive public vote-site connections directly. It consumes shared `VoteMessage` records from a DataProvider durable Redis stream, converts each delivery into either an external Votifier plugin event or ServerFeatures' native `VoteEvent`, waits for downstream tracked processing, then acknowledges the durable delivery.

ProxyFeatures or another trusted producer is responsible for authenticating/receiving vote-site traffic and publishing the shared contract.

## Commands, permissions and placeholders

Votifier registers no command, permission, PlaceholderAPI expansion or player-facing message.

It has no MySQL entity or audit table. Redis Streams and downstream feature state are the persistence boundary.

## Configuration

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `false` | Initializes DataProvider Redis and starts the durable subscription. |
| `channel` | `proxy.votifier.vote` | Durable Redis stream name. The key is called channel for compatibility, but the API used is a stream consumer. Blank falls back to the default. |
| `consumer_group` | empty | Explicit durable consumer group. Blank derives a group from global `server_name`. |

There are no feature settings for retry count, pending timeout, batch size, poll interval, dead-letter behavior, publisher token, socket port or vote-site secret. Those belong to DataProvider/producer configuration.

## Redis provider and startup

Initialization:

1. initializes feature DataProvider resources;
2. registers Redis messaging provider key `redis`, namespace/connection `hauntedmc`;
3. fails feature initialization when the provider is absent;
4. obtains `DurableMessagingDataAccess`;
5. resolves stream and consumer group;
6. constructs `EventBusHandler`;
7. starts one durable subscription.

Unlike optional Redis integrations such as Vanish, Redis is mandatory for this feature.

## Consumer-group identity

When `consumer_group` is nonblank, it is normalized:

- trim and lower-case;
- replace characters outside `[a-z0-9_.:-]` with `_`;
- collapse repeated underscores;
- fallback to `serverfeatures.votifier.server` when blank after normalization;
- truncate to 150 characters.

When blank, the group becomes:

```text
serverfeatures.votifier.<normalized global server_name>
```

Global `server_name` defaults to `server`. The feature warns when both the explicit group is blank and the server name is still the default, because multiple backends could accidentally share the same group.

Each feature instance creates a consumer name:

```text
<consumer group>.<random UUID>
```

### Distribution semantics

Redis Stream consumer groups distribute entries among consumers in the **same group**. Therefore:

- two backends using the same group compete/load-balance vote deliveries;
- two backends using distinct groups can each receive the same stream entry;
- restarting a backend creates a new consumer name in the same group and relies on DataProvider's durable recovery/pending behavior.

Configure group identity according to architecture. If only one reward backend should process each vote, use one logical group. If every backend independently needs every vote, every backend needs a unique group—but doing so with VoteReward on all servers can grant multiple network rewards unless reward ownership is also partitioned.

## Shared wire contract

The consumer filters for:

```text
VoteMessage.TYPE
VoteMessage.class
```

From each `DurableDelivery<VoteMessage>` it reads:

- `serviceName`;
- `username`;
- `address`, defaulting to `-` when null;
- `voteTimestamp`;
- durable event processing key.

A payload with null username or null service name is logged as invalid and deliberately acknowledged/discarded. Blank strings are not rejected here; downstream consumers such as VoteReward apply their own validation.

The processing key from the durable envelope is preserved across native/local dispatch and is the preferred idempotency key for VoteReward.

## Delivery lifecycle and acknowledgement ordering

The Redis callback does not directly call Bukkit events. It schedules one main-thread task through the feature task manager.

Inside that task:

1. choose external Votifier compatibility or native local dispatch;
2. obtain a `CompletionStage<Void>` representing tracked downstream work;
3. wait asynchronously for that stage;
4. acknowledge the Redis delivery only when the stage succeeds.

If scheduling, event dispatch or downstream tracked work fails, the delivery is not acknowledged. DataProvider/Redis pending-entry recovery can redeliver it later.

Acknowledgement itself is asynchronous. Ack failure is logged, but there is no feature-level retry wrapper; the durable provider determines whether the pending delivery is seen again.

## External Votifier compatibility

At `EventBusHandler` construction, compatibility is detected once:

1. plugin name `Votifier` must be enabled;
2. reflection must find:
   - `com.vexsoftware.votifier.model.Vote` no-arg constructor;
   - `VotifierEvent(Vote)` constructor;
   - setters for service, username, address and timestamp.

When unavailable/incompatible, the feature logs and uses native `VoteEvent` only. Enabling or updating the plugin later does not re-run detection until feature reconstruction.

### Reflected event dispatch

For a delivery, the feature:

- constructs external `Vote`;
- sets four string fields;
- calls external `VotifierEvent` synchronously through Bukkit's event manager.

Reflection failure logs severe and falls back to native local dispatch.

## `VoteDispatchTracker`

External Votifier events do not natively expose a downstream completion stage. ServerFeatures bridges that gap through a thread-local tracker:

1. open tracker with durable processing key;
2. synchronously dispatch external event;
3. ServerFeatures-aware listeners call:
   - `currentProcessingKey()` to reuse the durable key;
   - `trackCurrent(stage)` to register business work;
4. after event dispatch, retrieve combined completion;
5. close/remove thread-local tracker.

Only one tracker may be active per thread; nested durable vote dispatch throws.

All tracked stages are combined. Any failure fails the combined stage and prevents acknowledgement.

A third-party external Votifier listener that performs work but does not call `trackCurrent` is invisible to this acknowledgement contract. If no listener tracks anything, completion is already successful and the durable vote can be acknowledged immediately after synchronous event dispatch.

VoteReward's external listener supports this tracker.

## Native `VoteEvent`

When external compatibility is absent/fails, Votifier creates:

```java
new VoteEvent(new VotePayload(service, user, address, timestamp, processingKey))
```

It synchronously calls the Bukkit event manager and then uses `event.processingCompletion()`.

`VoteEvent#track` is synchronized and combines all supplied stages. Native listeners that perform asynchronous business work must call `track(...)`; otherwise the durable message can be acknowledged when synchronous event dispatch ends.

The event is not cancellable. Listener rejection should be represented as successful terminal processing when the message should be consumed, or an exceptional stage when it should remain pending/retry.

## Stable local processing keys

`VotePayload` always has a processing key. When absent/blank, it creates:

```text
vote.<name-based UUID of service NUL username NUL address NUL timestamp>
```

The transformation is deterministic for exactly equal strings/timestamp. Values are not lower-cased or trimmed before hashing.

For Redis deliveries, the durable envelope processing key is supplied and normally takes precedence over this fallback.

Null address becomes `-` in the record constructor. `getTimeStamp()` returns the numeric timestamp as text for Votifier compatibility.

## Event ordering and thread model

- Durable Redis callback: provider thread/context.
- Bukkit event dispatch: scheduled main server task.
- External/native event listener priorities: determined by listeners; Votifier itself simply calls the event manager.
- Downstream completion callback/ack: asynchronous future completion context.

No Bukkit event is dispatched after feature disable if lifecycle scheduling rejects/cancels it, but `EventBusHandler` has no explicit closed/generation check inside already-scheduled tasks. Subscription close is the primary admission boundary.

## Subscription completion and shutdown

After `consume`, the feature attaches to `subscription.completion()`. Unexpected termination logs severe.

On disable:

1. detach the local subscription reference;
2. call `closeAsync()`;
3. block up to five seconds waiting for confirmed closure;
4. restore interrupt status on interruption;
5. log warning on execution failure or timeout;
6. clear `eventBusHandler` in the feature.

There is no explicit wait for all already-dispatched downstream processing/ack futures after subscription closure. DataProvider's durable semantics must safely handle admitted in-flight deliveries.

## Integration with VoteReward

Recommended native configuration:

```yaml
Votifier:
  enabled: true
VoteReward:
  enabled: true
  vote_source: native
```

Flow:

```text
Proxy vote producer
→ Redis VoteMessage
→ ServerFeatures Votifier consumer
→ native VoteEvent
→ VoteReward NativeVoteListener
→ VoteEvent.track(VoteReward stage)
→ reward/queue + processed marker
→ Redis acknowledgement
```

External compatibility flow uses `VotifierEvent` plus `VoteDispatchTracker` and VoteReward's external listener.

Do not enable VoteReward with the wrong source: the native Votifier can dispatch an external event while VoteReward listens only native, or vice versa, resulting in no tracked reward consumer and potentially immediate acknowledgement.

## Failure and redelivery behavior

| Failure | Ack? | Result |
|---|---|---|
| Null service/username | Yes | Invalid message discarded. |
| Main task scheduling failure | No | Logged; eligible for durable recovery. |
| External reflection dispatch failure | Native fallback attempted | Ack depends on fallback downstream completion. |
| Native/local listener throws during dispatch | No | Logged as not dispatched. |
| Tracked downstream stage fails | No | Logged as not processed. |
| No tracked downstream work | Yes | Event dispatch alone is terminal. |
| Ack future fails | Not confirmed | Logged; provider may redeliver pending entry. |
| Consumer stops unexpectedly | — | Severe log; no self-created replacement subscription in this class. DataProvider may provide internal self-healing depending on its contract. |

The feature does not dead-letter poison messages. A structurally valid delivery whose listener consistently fails can remain pending/retrying indefinitely according to provider behavior.

## Security and trust boundary

This Paper consumer performs no signature/token/IP validation. It trusts `VoteMessage` records already in the configured Redis stream. Protect Redis access and authenticate vote ingress at the producer.

The consumer also makes shared payload data available to every registered native/external vote listener. Those listeners determine service whitelist and player identity policy.

## Important implementation boundaries

- It consumes Redis; it does not open a public vote port.
- Redis provider is mandatory.
- `channel` is a durable stream name.
- Consumer-group naming controls load balancing/fan-out.
- External compatibility is detected once by reflection.
- Downstream work must explicitly track a completion stage.
- Missing tracking can acknowledge before asynchronous third-party work finishes.
- Invalid null-field payloads are acknowledged/discarded.
- Processing failures intentionally remain unacknowledged.
- Ack failure has no feature-level retry loop.
- No database audit/dead-letter queue/command exists.
- Shutdown waits five seconds for subscription close, not all business futures.

## Verification checklist

1. Publish a valid shared `VoteMessage` and trace processing key through event, VoteReward and acknowledgement.
2. Run two consumers in the same group and distinct groups to confirm delivery semantics.
3. Leave `server_name=server` on multiple backends and verify the warning/routing consequences.
4. Publish null, blank and malformed payload fields and inspect discard versus downstream validation.
5. Test native flow with multiple listeners tracking success/failure stages.
6. Test an async listener that does not track and observe acknowledgement timing.
7. Enable compatible external Votifier and verify ThreadLocal processing-key propagation.
8. Break reflection compatibility and confirm native fallback.
9. Fail main scheduling, downstream processing and acknowledgement independently.
10. Restart Redis/consumer and verify pending delivery recovery through DataProvider.
11. Disable during in-flight dispatch and inspect close/ack behavior.
12. Verify VoteReward `vote_source` matches the event path used.

## Source map

- Config/provider/group/subscription lifecycle: `features/votifier/Votifier.java`
- Durable delivery, dispatch, acknowledgement and close: `features/votifier/internal/EventBusHandler.java`
- Native event completion contract: `features/votifier/event/VoteEvent.java`
- Payload/stable key: `features/votifier/event/VotePayload.java`
- External event completion bridge: `features/votifier/event/VoteDispatchTracker.java`
- Shared wire contract: ProxyFeatures contracts `VoteMessage`
