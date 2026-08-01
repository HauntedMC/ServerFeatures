# CommandRelay

> Paper · Feature name `CommandRelay` · feature package `features.commandrelay` · disabled by default

CommandRelay publishes and consumes durable Redis-stream messages carrying console commands between network components. A backend can be configured as a sender, listener, both, or neither. Incoming commands are validated against an exact root-command allowlist, dispatched on the Paper main thread as console, recorded in an optional MySQL audit table, protected against completed-message replay by a persistent file ledger, and acknowledged through DataProvider's durable messaging API.

The delivery model is retry-oriented but not transactionally exactly-once. Command execution happens before completion-ledger persistence and acknowledgement, so a process failure in that interval can execute the same operation again after redelivery.

## Modes and registration

| Setting | Effect |
|---|---|
| `listening: true` | Subscribe to this backend's durable command stream. |
| `sending: true` | Register `/commandrelay` so local senders can publish to target streams. |
| both true | Consume and publish. |
| both false | Initializes DataProvider, audit ORM, Redis bus and replay ledger but registers no command/subscription. |

Redis is a hard startup requirement in every mode: failure to register the durable messaging provider throws `IllegalStateException`, even when both sending/listening are false.

Audit database is optional; replay ledger is mandatory.

## Command and permission

| Syntax | Permission | Sender | Meaning |
|---|---|---|---|
| `/commandrelay <targetServer> <command...>` | `serverfeatures.feature.commandrelay.command.use` | Player or console | Publish a durable `CommandRelayMessage` to `<targetServer>.commandrelay.command`. |

The command exists only when `sending` was true during feature initialization. It has no aliases or tab completion.

- Target text is used exactly as supplied to construct the stream; it is not trimmed, lowercased, validated against registered servers, or normalized.
- Command arguments are joined with single spaces.
- A leading slash in the published command is allowed; receivers remove one leading slash before dispatch.
- Publication completion reports `commandrelay.relayed` when the Redis publish future completes normally and `commandrelay.relay_failed` on exceptional completion.
- Successful publication means the durable event was accepted by messaging, **not** that a receiver validated or executed it.
- No result/response message is sent back from the target.

The completion callback schedules sender feedback through the feature task manager. It does not check whether a player sender remains online before sending.

## Complete configuration reference

File: `plugins/ServerFeatures/features/CommandRelay/config.yml`.

| Key | Default | Meaning and constraints |
|---|---:|---|
| `enabled` | `false` | Enables feature initialization. |
| `listening` | `false` | Enables durable consumption for the normalized local `server_name`. Directly cast to `Boolean`. |
| `sending` | `false` | Registers the command. Directly cast to `Boolean`. |
| `consumer_group` | empty | Optional explicit durable consumer-group key. Blank derives `serverfeatures.commandrelay.<normalized-server>`. Normalized/lowercased and capped at 150 characters. |
| `processed_command_ttl_millis` | `691200000` | Replay marker TTL (8 days). Must be a positive numeric value; otherwise warning and fallback. |
| `command_whitelist` | empty list | Exact allowed **main command aliases**, without slash. Read on every delivery, trimmed/lowercased into a set. Empty list means every incoming command is rejected. |

Global setting:

| Key | Default/use |
|---|---|
| `server_name` | fallback `server`; normalized for listening stream/group and used as origin text when publishing. |

There are no settings for Redis connection/channel prefix beyond shared provider IDs, target allowlist, dispatch timeout, retry count, result responses, command arguments/redaction, audit enable toggle, max command length, or sender identity authorization.

## Server and consumer-key normalization

Listening server and consumer-group values are:

1. trim;
2. lowercase with `Locale.ROOT`;
3. replace characters outside `[a-z0-9_.:-]` with `_`;
4. collapse consecutive underscores;
5. use fallback when blank;
6. truncate to 150 characters.

Default listening stream:

```text
<normalized server_name>.commandrelay.command
```

Derived group:

```text
serverfeatures.commandrelay.<normalized server_name>
```

Explicit `consumer_group` is normalized using fallback `serverfeatures.commandrelay.server`.

The unique consumer name is:

```text
<consumer-group>.<random UUID>
```

This permits multiple runtime consumer instances in the same durable group while preserving group-level delivery ownership.

Publishing does not use this target normalization, so operators must submit exactly the normalized target stream prefix expected by the listener.

## Durable message contract

Shared contract: `nl.hauntedmc.proxyfeatures.contracts.messaging.CommandRelayMessage`.

Publication builds:

- command text;
- origin server from global setting, trimmed with fallback `server`;
- message-generated operation ID;
- `DurableEvent` event ID = operation ID;
- processing key = operation ID;
- type = `CommandRelayMessage.TYPE`.

Receiver validates:

1. payload non-null;
2. non-blank message operation ID;
3. operation ID exactly equals durable processing key;
4. non-blank origin server;
5. non-blank command.

Invalid/null messages are audited, acknowledged, and permanently discarded.

There is no target-server field in the payload; targeting is entirely determined by which stream received the event.

## Incoming validation and dispatch pipeline

For each durable delivery:

1. validate payload/envelope;
2. check persistent completed ledger;
3. acquire in-memory `activeOperations` ownership for processing key;
4. remove one leading slash and trim;
5. derive main command as text before first literal space;
6. rebuild exact lowercase whitelist set from config;
7. reject/ack when root is not allowed;
8. schedule main-thread console dispatch;
9. call `Bukkit.getServer().dispatchCommand(console, command)`;
10. audit `executed` or `dispatch_rejected` based on boolean result;
11. asynchronously persist completion marker;
12. only after marker success, remove active key and acknowledge delivery.

### Whitelist semantics

- Comparison is case-insensitive after trim/lowercase.
- Only the first root alias is checked; all remaining arguments are unrestricted.
- Namespaced roots must be explicitly allowlisted in the exact form used.
- A whitelisted wrapper command can potentially invoke broader command behaviour through its arguments; audit allowlisted command implementations carefully.
- Whitelist is receiver-side and re-read per delivery, so config reload visibility depends on ConfigHandler state; no feature reconstruction is required for the list if handler reads live values.
- The sender performs no whitelist check.

### Console execution

Incoming commands execute as Paper console with full console authority. Sender player identity/permissions are not transmitted or reproduced. Origin server is informational/audit data only.

`dispatchCommand` returning false is treated as a terminal `dispatch_rejected`: the operation is marked processed and acknowledged, with no retry. A thrown runtime exception leaves the delivery unacknowledged and unprocessed so durable redelivery can retry.

## Delivery guarantees and failure windows

### Invalid/forbidden/replayed

These are acknowledged immediately/asynchronously and do not retry:

- null payload;
- invalid operation ID/origin/command;
- blank root after slash removal;
- command not in whitelist;
- operation already in completed ledger.

### Scheduling or dispatch exception

The active-operation key is removed, an audit attempt is made, and the delivery is **not acknowledged**. It remains eligible for durable retry.

### Completion persistence failure

After a command returns normally, failure to write the processed marker means no acknowledgement. Redelivery can execute the command again.

### Crash windows

- Before dispatch: retry, no prior execution.
- During/after dispatch but before marker persistence: possible duplicate execution.
- After marker persistence but before acknowledgement: redelivery is recognized by ledger and acknowledged without execution.

Therefore commands should be idempotent where possible or include their own operation-level deduplication when duplicate side effects are unacceptable.

### Concurrent duplicate delivery

`activeOperations.add(processingKey)` gates one local in-flight handler. A simultaneous duplicate that finds the key active simply returns without acknowledgement. The original handler is expected to complete/acknowledge; otherwise durable pending recovery handles it later.

## Persistent replay ledger

Storage is created through feature cache manager:

```text
feature cache directory: CommandRelay / durable-commandrelay
store: processed
type: JSON
```

At construction, `ProcessedCommandLedger` loads all current store keys into a concurrent in-memory set.

On completion:

1. build `CacheValue` with configured TTL and `processed=true`;
2. synchronize on `FileCacheStore` and `put(processingKey, marker)`;
3. add key to in-memory set.

Important semantics:

- The file marker has TTL through cache infrastructure.
- The in-memory `processedKeys` set has no removal/expiry logic. A marker loaded/added remains considered processed for the lifetime of the current feature instance even after its file TTL expires.
- Reload reconstructs the set from whatever unexpired/listed keys the store exposes.
- The set can grow with unique operations during long uptime.
- File write must succeed before acknowledgement.

The default eight-day TTL protects replay across restarts while limiting persistent cache retention, but runtime memory suppression can be longer until reload.

## Audit database integration

Startup attempts optional MySQL connection:

- identifier: `commandRelayAudit`;
- access/connection policy: `system_data_rw`;
- ORM entity: `CommandRelayAuditLogEntity`.

When unavailable, a warning is logged and `CommandRelayAuditLogService` silently no-ops. Redis relay still functions.

Table: `command_relay_logs`

| Column | Length/nullability | Meaning |
|---|---|---|
| `id` | identity PK | Audit row. |
| `relay_channel` | 100, nullable | Stream name. |
| `origin_server` | 100, nullable | Payload origin. |
| `command_alias` | 64, nullable | Derived command root. |
| `command_text` | 512, nullable | Full command after receiver normalization. |
| `event_type` | 64, non-null | Outcome/category. |
| `details` | 512, nullable | Validation/error detail. |
| `created_at` | non-null epoch ms | Audit admission time. |

Indexes:

- `(origin_server, created_at)`;
- `(command_alias, created_at)`;
- `(event_type, created_at)`.

Audit values are trimmed and truncated to mapped lengths; blank becomes null, except event type falls back to `unknown`. Writes are asynchronous, best-effort and independent of relay acknowledgements. Audit failure never blocks execution or retry logic.

Event types emitted:

| Event | Meaning |
|---|---|
| `invalid_payload` | Null/missing/mismatched required envelope/command fields. |
| `replay_ignored` | Persistent ledger already marked operation complete. |
| `forbidden_command` | Root absent from receiver whitelist. |
| `executed` | Bukkit dispatch returned true. |
| `dispatch_rejected` | Bukkit dispatch returned false. |
| `dispatch_error` | Scheduling/dispatch threw; includes error details. |

There is no audit event for publication attempts/success/failure on the sending backend.

## Messages and variables

| Key | Variables | Use |
|---|---|---|
| `commandrelay.usage` | none | Fewer than target+command. |
| `commandrelay.relayed` | `{target}`, `{cmd}` | Durable publication completed normally. |
| `commandrelay.relay_failed` | `{target}`, `{cmd}` | Publication future failed. |

The command text—including potentially sensitive arguments—is displayed back to sender and stored in receiving audit rows. No redaction exists.

## Subscription lifecycle and shutdown

`consume()` requires a non-null durable subscription and attaches a completion failure logger. Consumer setup exceptions are logged severe and rethrown.

Disable:

1. move current subscription to local variable;
2. clear field;
3. call `closeAsync()`;
4. block up to five seconds waiting for close confirmation;
5. restore interrupt flag on interruption;
6. warn on timeout/execution/runtime failure.

This close wait occurs in feature disable and can delay shutdown up to five seconds. `activeOperations` and ledger have no explicit shutdown clear.

Callbacks/tasks already admitted rely on lifecycle/DataProvider fencing. Main-thread dispatch and async completion persistence must be allowed to terminate safely or leave the durable message pending for another consumer.

## Security model

CommandRelay is remote console execution. Security depends on:

- Redis/DataProvider connection isolation;
- shared contract integrity;
- operation-ID validation;
- stream naming;
- strict receiver-side command whitelist;
- restricted `/commandrelay` permission;
- safe allowlisted command argument semantics;
- database/cache filesystem permissions.

The payload has no cryptographic signature, sender player identity, per-origin allowlist, target field, nonce expiry or command-argument policy in this feature. Any trusted publisher able to write a valid event to the stream can request any allowlisted console command.

Never allowlist broad arbitrary-dispatch/eval/plugin-management commands without additional validation.

## Persistence and messaging summary

- Durable Redis provider IDs: `redis` / `hauntedmc`.
- Stream: `<server>.commandrelay.command`.
- Group: explicit or `serverfeatures.commandrelay.<server>`.
- Shared type: `CommandRelayMessage.TYPE`.
- Completion replay store: JSON file cache.
- Optional audit: MySQL `system_data_rw`.
- PlaceholderAPI/API: none.
- Result response stream: none.

## Developer source map

- Config/integration/lifecycle: `features/commandrelay/CommandRelay.java`
- Send command: `features/commandrelay/command/CommandRelayCommand.java`
- Durable pipeline: `features/commandrelay/internal/EventBusHandler.java`
- Replay ledger: `features/commandrelay/internal/ProcessedCommandLedger.java`
- Audit service/entity: `features/commandrelay/audit/`
- Shared message contract: ProxyFeatures contracts API `CommandRelayMessage`
- Tests: `src/test/.../features/commandrelay/`

## Operational verification

1. Test sending-only, listening-only, both and neither modes.
2. Verify exact target normalization mismatch behaviour.
3. Start with Redis unavailable and audit DB unavailable separately.
4. Verify empty whitelist rejects/acks every command.
5. Test allowed root case, leading slash, namespaced alias, unrestricted arguments and false dispatch.
6. Publish malformed operation/origin/command envelopes and inspect audit/ack.
7. Redeliver the same operation concurrently and after completion/restart.
8. Simulate crash after dispatch before marker and verify possible duplicate execution.
9. Simulate marker success/ack failure and verify replay is ignored.
10. Test marker TTL/reload and long-running in-memory retention.
11. Verify audit truncation/indexes and relay independence from audit failure.
12. Disable with active consumer/dispatch and inspect five-second close/fencing.
13. Review every allowlisted command for idempotence and secret-bearing arguments.

## Troubleshooting

- **Published but never executed:** publication success is not execution; check exact target stream, listener mode/group, whitelist, durable pending state and receiver logs/audit.
- **Forbidden despite whitelist:** compare the exact first root alias after one leading slash removal; config is case-insensitive but not alias-expanding.
- **Command ran twice:** crash/persistence failure can occur after dispatch before completion marker. Make command idempotent or add transactional operation deduplication.
- **Old operation remains ignored beyond TTL:** in-memory processed set does not expire until feature reconstruction.
- **Audit table empty but commands run:** optional `system_data_rw` ORM was unavailable or async audit writes failed.
- **Sender sees relayed but target rejects:** relayed means Redis publish only; no execution response exists.
- **Shutdown pauses:** subscription close waits up to five seconds.
- **Sensitive command text stored:** no redaction exists in command feedback/audit.
