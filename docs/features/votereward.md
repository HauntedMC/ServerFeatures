# VoteReward

> Paper · Feature ID `votereward` · disabled by default · native tracked vote rewards

VoteReward is the only supported reward consumer for ServerFeatures Votifier's native `VoteEvent`. It validates vote services and player identities, grants configured console-command rewards to online players, stores rewards for known offline players, deduplicates durable processing keys, and replays queued rewards when players join.

There is no vote-source selector, external Votifier listener, NuVotifier event bridge, compatibility fallback, or automatic source switching. VoteReward requires the ServerFeatures Votifier feature to be enabled and fails initialization otherwise.

## Required feature relationship

```yaml
Votifier:
  enabled: true

VoteReward:
  enabled: true
```

Flow:

```text
ProxyFeatures persistent backend outbox
→ private Redis vote stream
→ ServerFeatures Votifier
→ native tracked VoteEvent
→ VoteReward NativeVoteListener
→ VoteHandler completion stage
→ Redis acknowledgement
```

`NativeVoteListener` handles `VoteEvent` at `NORMAL` priority with `ignoreCancelled=true`, preserves the durable processing key, and registers the complete reward stage through `event.track(...)`. Upstream acknowledgement therefore waits for VoteReward's terminal result.

## Configuration

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `false` | Enables cache storage, native vote listener, and join replay listener. |
| `vote_whitelist` | `SERVERPACTTEST`, `TopMinecraftServers`, `SERVERPACT.NL`, `minecraftkrant.nl`, `Minecraft-MP.com` | Exact accepted service names. Empty rejects all votes. |
| `rewards` | `eco give {player} 10` | Console command templates executed once per delivered vote. |
| `join_message_delay` | `100` ticks | Delay before the queued-vote count message. |
| `rewards_start_delay` | `100` ticks | Additional delay before queued reward replay starts. |
| `reward_interval` | `20` ticks | Spacing between queued vote deliveries. |
| `cache_ttl_millis` | `86400000` | Offline queued-vote expiry, default 24 hours. |
| `processed_vote_ttl_millis` | `691200000` | Processed-key marker expiry, default eight days. |

The removed `vote_source` key has no effect and should be deleted from configuration.

Delay values are read as integers and TTL values through `Number`. Their effective scheduler/cache behavior should be validated before setting zero or negative values.

The default offline-retrieval message states 24 hours. Update that message separately when changing `cache_ttl_millis`.

## Service whitelist

`vote_whitelist` matching is exact and case-sensitive. Incoming service names are not trimmed or lower-cased before membership checking.

A null service or a service absent from the list is rejected and logged. Rejection is terminal business processing: the completion stage succeeds so the structurally valid Redis delivery can be acknowledged rather than retried forever.

## Player identity

VoteReward first tries exact online-name lookup through Bukkit.

For offline players, `PlayerIdentityResolver.findByUsername` queries DataRegistry. A vote is accepted only when a canonical player identity exists. Unknown names are rejected rather than creating arbitrary offline cache entries.

After asynchronous identity lookup, processing returns to the main-thread path and rechecks whether the UUID is online. This handles a player joining while identity resolution is running.

Offline state is keyed by canonical UUID.

## Online reward delivery

For an online player:

1. broadcast `votereward.vote_broadcast` to online audiences;
2. send `votereward.vote_received` to the recipient;
3. replace every `{player}` occurrence in each reward command;
4. execute commands as console in configured order;
5. complete vote processing;
6. persist the durable processed-key marker.

VoteReward does not own an economy or item transaction. Command behavior, atomicity, and rollback belong to the target command providers.

An empty reward list consumes and marks the vote but grants nothing. A thrown command-dispatch failure can stop later commands and fail the tracked vote stage.

## Offline queue

Offline votes are stored below:

```text
<VoteReward cache>/players/<canonical UUID>/
```

Each JSON cache value contains:

- configured TTL;
- vote service.

The entry key is the normalized durable processing key when present. A random timestamp-based key is used only for a vote constructed without a processing key; normal Redis ingress always supplies one.

The public vote broadcast is scheduled only after the offline cache write succeeds. The global processed marker is then persisted so ordinary durable redelivery does not queue the same obligation again.

## Processed-key idempotency

A separate `_processed-votes` store contains TTL markers. Existing active keys are loaded into a concurrent set at handler construction.

For each nonblank processing key:

1. trim the key;
2. return immediately when already processed;
3. use an in-process map to coalesce concurrent identical deliveries;
4. run validation and online/offline handling once;
5. persist the processed marker;
6. add the key to the in-memory set;
7. complete all coalesced callers.

The immutable key comes from ProxyFeatures and remains unchanged across immediate retries, proxy restarts, backend downtime, and fresh-timestamp replay.

The processed marker's default eight-day TTL exceeds the proxy outbox's maximum 24-hour retention.

## Idempotency boundaries

Reward execution and marker/cache mutation are not one database transaction.

### Online grant window

Commands execute before marker persistence. A crash or write failure after commands but before the marker becomes durable can permit the same vote to grant again on redelivery.

### Offline queue window

The queue entry is written before the processed marker. A crash between them may allow ingress redelivery. Because the queue key normally equals the processing key, cache replacement semantics reduce duplication, but this is not a transactional guarantee.

### Join replay window

The queued entry is removed after reward commands execute. A crash, disconnect race, or removal failure after granting can replay the reward on a later join.

For stronger financial-grade guarantees, configured rewards should be idempotent or implemented through a dedicated durable reward ledger with explicit states.

## Join replay

`VoteJoinListener` invokes offline replay at `NORMAL` priority.

A random replay generation UUID fences delayed tasks from older joins or replay attempts.

Before loading votes, DataRegistry provides current and historical usernames. VoteReward inspects:

- the canonical UUID store;
- historical-name stores that may contain pending state for the same identity.

This historical-name lookup supports renamed players; it is not an alternate vote ingress path.

Expired entries are removed during collection. Every pending item retains its source store and key so successful replay removes the correct file entry.

### Ordering

When pending votes exist:

1. after `join_message_delay`, send the queued count;
2. after the additional `rewards_start_delay`, schedule replay tasks;
3. space tasks by `index × reward_interval`;
4. verify current replay generation and online state;
5. execute normal online reward delivery;
6. remove the source cache entry asynchronously;
7. clear the replay generation when the final scheduled task finishes.

Cache iteration order is not explicitly chronological.

A player disconnecting before a scheduled grant leaves the entry for a future join. A newer replay generation makes callbacks from an older join inert.

## Messages

| Key | Variables / audience |
|---|---|
| `votereward.vote_received` | Sent to the online recipient after accepted processing. |
| `votereward.offline_votes_retrieved` | `{count}` sent after join delay. |
| `votereward.vote_broadcast` | `{player}` sent to online audiences for accepted ingress. |

Rejected services, unknown players, duplicates, cache failures, and command failures are logged rather than shown to players.

## Threading

`handleVote` schedules onto the main server path when invoked off-thread.

Bukkit lookups, messages, broadcasts, and command dispatch run on the main path. DataRegistry and file-cache work run asynchronously, with Bukkit operations rescheduled appropriately.

The completion stage returned by `VoteHandler` covers the work that must finish before Votifier acknowledges the Redis delivery.

## Failure behavior

| Failure | VoteEvent stage | Upstream result |
|---|---|---|
| Service rejected | Success | Redis delivery can be acknowledged. |
| Unknown player identity | Success | Delivery is terminally rejected and acknowledged. |
| Duplicate processing key | Success | No second reward; delivery acknowledged. |
| Offline cache write fails | Failure | Redis delivery remains pending. |
| Reward command processing fails | Failure | Delivery remains pending; duplicate window depends on marker state. |
| Processed marker write fails | Failure | Delivery remains pending. |
| Queued-entry removal fails after join grant | Replay entry remains | May grant again on later join. |

## Persistence and scope

VoteReward state is local JSON cache, not MySQL. Each backend has its own reward obligations, queue, and processed-key store. The same immutable vote key can therefore be processed independently on every intended gamemode without one backend suppressing another.

Moving a player to another backend does not move that backend's queued rewards unless storage is shared separately.

## Commands, permissions, placeholders, and APIs

VoteReward registers no command, permission, or PlaceholderAPI expansion. It exposes its `VoteHandler` through the concrete feature instance but does not register a separate public service API.

There is no manual queue inspection, replay, deletion, or reward audit command.

## Important boundaries

- Votifier is a hard feature dependency.
- Native `VoteEvent` is the only ingress.
- Service matching is exact and case-sensitive.
- Offline players must already exist in DataRegistry.
- Rewards are console commands without transaction rollback.
- Durable keys deduplicate only after marker persistence.
- Offline and processed state use local file cache.
- Join replay order is not guaranteed chronological.
- Entry removal occurs after command execution.
- Message retention text can diverge from configured TTL.

## Verification checklist

1. Enable Votifier and VoteReward and confirm native listener registration.
2. Disable Votifier and confirm VoteReward fails initialization rather than silently loading without a source.
3. Test exact whitelist capitalization, null service, and empty whitelist.
4. Deliver the same processing key concurrently and after restart.
5. Vote for online, known offline, renamed, and unknown players.
6. Inspect UUID/historical-name cache lookup and TTL expiry.
7. Disconnect and reconnect during count delay, start delay, and replay interval.
8. Force cache write, marker write, and entry-removal failures independently.
9. Use multiple reward commands with one failing command.
10. Confirm `VoteEvent.track` prevents Redis acknowledgement until reward processing completes.
11. Confirm the same key rewards independently on two intended backend servers.

## Source map

- Defaults, dependency validation, and listener registration: `features/votereward/VoteReward.java`
- Validation, identity, rewards, cache, deduplication, and replay: `features/votereward/internal/VoteHandler.java`
- Shared ingress record/key: `features/votereward/internal/IncomingVote.java`
- Native tracked event listener: `features/votereward/listener/NativeVoteListener.java`
- Join replay trigger: `features/votereward/listener/VoteJoinListener.java`
