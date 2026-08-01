# VoteReward

> Paper · Feature ID `votereward` · disabled by default · native or external Votifier ingress

VoteReward validates incoming votes, broadcasts accepted votes, executes configured console reward commands for online players, and stores rewards for known offline players in feature JSON cache files. On join it loads UUID and legacy-name cache stores, announces the queued count and replays rewards at a configurable interval.

It includes stable vote-key deduplication and in-process coalescing, but reward execution, offline-cache removal and processed-marker persistence are separate steps. The exact duplicate-delivery windows are documented below.

## Vote source selection

| Key | Default | Behavior |
|---|---|---|
| `vote_source` | `native` | `native` listens to ServerFeatures Votifier's `VoteEvent`; `votifier` listens to external Votifier plugin `VotifierEvent`. |

Values are trimmed/lower-cased. Null/blank resolves to native. Unknown nonblank values log a warning and default to native.

Only one listener is registered. The feature warns when the selected provider is not enabled, but remains loaded and does not switch automatically later.

### Native ingress

`NativeVoteListener` handles ServerFeatures `VoteEvent` at `NORMAL`, `ignoreCancelled=true`. It converts the payload without changing its processing key and attaches the returned completion stage through `event.track(...)`. This lets the native Votifier dispatch wait for downstream completion before acknowledging its own durable work.

### External Votifier ingress

`VotifierVoteListener` handles `VotifierEvent` at `NORMAL`, `ignoreCancelled=true`.

- timestamp text is parsed as a long;
- parse failure uses current epoch milliseconds;
- a stable key is generated from service, username, address and timestamp;
- when ServerFeatures' `VoteDispatchTracker` already exposes a current processing key, that key is preferred;
- completion is attached back to the current tracker where one exists.

The external event itself has no durable acknowledgement contract controlled by VoteReward.

## Configuration

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `false` | Enables cache, selected vote listener and join replay listener. |
| `vote_whitelist` | `SERVERPACTTEST`, `TopMinecraftServers`, `SERVERPACT.NL`, `minecraftkrant.nl`, `Minecraft-MP.com` | Exact accepted service names. Empty list rejects all votes. |
| `rewards` | `eco give {player} 10` | Console command templates executed once per delivered vote. |
| `join_message_delay` | `100` ticks | Delay before queued-vote count message. |
| `rewards_start_delay` | `100` ticks | Additional delay after the message delay before replay scheduling begins. |
| `reward_interval` | `20` ticks | Spacing between queued vote deliveries. |
| `cache_ttl_millis` | `86400000` | Expiry for an offline queued vote, default 24 hours. |
| `processed_vote_ttl_millis` | `691200000` | Expiry for processed-key markers, default 8 days. |

Delay values are directly cast to `int`; TTL values are cast through `Number`. Negative/zero values are not clamped in `VoteHandler` and depend on the cache/scheduler implementation.

The default offline-retrieval message states 24 hours statically. If `cache_ttl_millis` changes, update that text separately.

### Whitelist matching

`whitelist.contains(service)` is exact and case-sensitive. There is no trim or case normalization of the incoming service name. A null service or nonmatching capitalization is rejected and logged.

### Reward commands

For each configured template, every literal occurrence of `{player}` is replaced with the current online player's name and dispatched immediately as console. Commands run in list order. No leading slash is stripped and no command success result is inspected.

An empty list still consumes/marks the vote but gives no reward. A failure/exception from one dispatch can interrupt later commands and fail the vote stage.

## Identity resolution

Votes first try exact online-name lookup with `Bukkit.getPlayerExact(suppliedUsername)`.

For an offline name, `PlayerIdentityResolver.findByUsername` queries DataRegistry. The vote is accepted only when a canonical `player_entity` identity exists. Unknown names are rejected rather than creating an offline-player row or caching by arbitrary username.

After asynchronous identity lookup, the handler returns to the main task path and rechecks the UUID online. This handles a player joining during lookup.

The persisted/offline cache key is the canonical UUID string.

## Online delivery

For an online player:

1. broadcast `votereward.vote_broadcast` separately to every online audience;
2. send `votereward.vote_received` to the recipient;
3. dispatch every reward command synchronously as console;
4. complete vote processing;
5. asynchronously persist the processed-key marker when a key exists.

Broadcast and reward messages use each audience's localization context.

There is no economy/item transaction owned by VoteReward; reward semantics belong to the configured commands and their plugins.

## Offline queue storage

Feature cache directory:

```text
<VoteReward cache>/players/<canonical UUID>/
```

Each vote is a JSON `CacheValue` containing:

- TTL from `cache_ttl_millis`;
- field `service`.

The cache entry key is:

- normalized processing key when present;
- otherwise `vote_<epoch>_<random UUID>`.

Address, username and timestamp are not stored as fields in the offline cache beyond information encoded in a stable processing key.

Queuing runs asynchronously. The public vote broadcast is scheduled on the main thread only after the cache write stage completes successfully.

After the offline queue operation completes, the global processed-vote marker is written. Thus a repeated ingress event with the same key is ignored even though the actual reward remains pending for join replay.

## Processed-vote deduplication

A separate JSON store `_processed-votes` contains TTL markers. Its existing keys are loaded into a concurrent set when `VoteHandler` is created.

For a nonblank processing key:

1. trim it;
2. ignore immediately when present in the processed set;
3. use `voteProcessing.putIfAbsent` to coalesce concurrent same-key deliveries in this process;
4. process the vote;
5. asynchronously write a processed marker;
6. add the key to the in-memory processed set;
7. complete all callers waiting on that key.

The marker value contains `processed=true` and the configured processed TTL.

Expired marker cleanup depends on `FileCacheStore.listAll`/cache semantics. Keys loaded into `processedVoteKeys` are not explicitly checked for expiry in the visible constructor, so correct startup behavior depends on the cache store excluding/removing expired values.

Votes with null/blank processing keys bypass both persistent deduplication and in-process coalescing.

## Duplicate-delivery windows

The design prevents ordinary retries after successful terminal persistence, but it is not one atomic transaction.

### Online vote

Reward commands execute before the processed marker is written. A process crash or marker-write failure after commands but before durable marker completion can allow the same vote to grant again on redelivery.

### Offline queue ingress

The offline cache entry is written before the processed marker. A crash in between can leave the queued reward while ingress redelivery queues the same key again. Because the queue key normally equals processing key, store replacement semantics may limit duplication, but that behavior belongs to `FileCacheStore` and must be tested.

### Join replay

A queued cache entry is removed asynchronously **after** `processVote(current)` executes. A crash, disconnect timing or removal failure after rewards can replay that cache entry on the next join.

There is no database transaction/outbox connecting command execution and marker/cache mutation. Configured reward commands should therefore be idempotent where possible, or the architecture should move grants into a durable ledger with terminal states.

## Join replay lifecycle

`VoteJoinListener` handles `PlayerJoinEvent` at `NORMAL`, `ignoreCancelled=true` and calls `processOfflineVotesOnJoin`.

A fresh random replay generation UUID is stored per player, fencing delayed work from an older join/replay attempt.

### Legacy cache-name migration

Before loading votes, the handler waits for DataRegistry identity and requests up to 100 historical player names. It builds a normalized lower-case set containing current and historical usernames.

Pending entries are collected from:

- canonical UUID store;
- every distinct legacy-name store.

Expired cache values are removed during collection. Nonexpired values are represented by their originating store and key, so successful replay removes them from the correct legacy/UUID file.

Identity/history failure falls back to current normalized name plus UUID store and logs a warning.

### Replay ordering

When pending entries exist:

1. after `join_message_delay`, send `offline_votes_retrieved` with total count;
2. after `join_message_delay + rewards_start_delay`, schedule one task per vote;
3. each vote task is delayed by `index × reward_interval`;
4. re-resolve player by UUID and require current replay generation/online state;
5. execute normal online reward messaging and commands;
6. asynchronously remove that cache entry;
7. the final scheduled vote clears the generation in a `finally` block.

The pending list order comes from cache-store iteration and legacy-store traversal; it is not explicitly sorted by timestamp or service.

If the player disconnects before a delivery, the entry is left for a later join. If the last scheduled task runs while offline, its `finally` still clears the generation.

Starting another replay replaces the generation, making older delayed callbacks inert.

## Messages

| Key | Variables / audience |
|---|---|
| `votereward.vote_received` | Recipient after online/replayed delivery. The handler also supplies `{player}`, though the default text does not use it. |
| `votereward.offline_votes_retrieved` | `{count}` after join delay. |
| `votereward.vote_broadcast` | `{player}` to every online player for accepted ingress; queued replay itself does not rebroadcast. |

There is no player-facing message for rejected service, unknown identity, command failure, cache failure or duplicate suppression.

## Threading and event safety

`handleVote` normalizes any off-main invocation by scheduling a main-thread stage. Bukkit player lookups, broadcasts and command dispatch occur on the main path.

DataRegistry identity/history and file-cache work run asynchronously. Completion returns to main before Bukkit operations.

Config lists/delays are captured at handler construction. There is no explicit closed/generation flag for the whole feature; lifecycle task management and per-player replay generations provide most fencing.

## Commands, permissions and APIs

VoteReward registers no player/staff command, permission or PlaceholderAPI expansion. `getVoteHandler()` is exposed on the concrete feature but no public service is registered through the API manager.

There is no manual replay/inspect/delete command or reward audit UI.

## Persistence and observability

Persistence is local file cache, not MySQL. Moving a player to another backend does not share queued votes unless cache storage itself is shared externally. The native Votifier may route votes to the intended reward backend, but VoteReward has no Redis/network queue of its own.

Logs cover accepted/rejected vote decisions, unknown identities, cache failures and processed/replay problems. Successful reward command results are not audited.

## Important implementation boundaries

- Service whitelist is exact/case-sensitive.
- Only one ingress source is active.
- Missing selected source logs but does not auto-switch.
- Offline players must already exist in DataRegistry.
- Rewards are arbitrary console commands with no rollback/result verification.
- Stable processing keys provide deduplication only after marker persistence.
- Blank processing keys are not deduplicated.
- Offline and processed state use local JSON cache.
- Join replay order is not explicitly chronological.
- Cache removal occurs after command execution and can fail.
- Static message retention text can diverge from configured TTL.
- There is no command/permission/manual audit path.

## Verification checklist

1. Exercise native and external source selection with the other source also installed; verify only one listener grants.
2. Test exact whitelist capitalization, null/blank service and empty whitelist.
3. Deliver the same processing key concurrently and after restart.
4. Interrupt online processing between commands and marker persistence in a disposable environment.
5. Vote for online, known offline, renamed and unknown players.
6. Inspect UUID and legacy-name cache files, TTL expiry and name-history migration.
7. Disconnect/reconnect during count delay, start delay and reward interval.
8. Force cache-entry removal failure after a granted replay and confirm next-join behavior.
9. Use multiple reward commands with one failing/throwing command.
10. Test zero/negative delays, interval and TTL values.
11. Verify local-cache behavior when players join a different backend.
12. Confirm native `VoteEvent.track` does not acknowledge upstream before VoteReward completion.

## Source map

- Defaults/source selection/cache setup: `features/votereward/VoteReward.java`
- Validation, identity, rewards, cache, deduplication and replay: `features/votereward/internal/VoteHandler.java`
- Shared ingress record/key: `features/votereward/internal/IncomingVote.java`
- Native event bridge: `features/votereward/listener/NativeVoteListener.java`
- External Votifier bridge: `features/votereward/listener/VotifierVoteListener.java`
- Join replay trigger: `features/votereward/listener/VoteJoinListener.java`
