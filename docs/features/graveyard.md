# Graveyard

> Paper · Feature name `Graveyard` · feature package `features.graveyard` · disabled by default

Graveyard replaces physical death chests with durable, packet-only graves. A successful capture transfers the final death-event drops and a configurable share of dropped experience into a checksummed payload. Nearby clients receive virtual block displays, one text display, and an invisible interaction entity; no block or Bukkit entity is placed in the world.

The implementation is deliberately fail-safe. Death drops are changed only after a local `PREPARED` journal record has been synchronously forced to disk. Claims use a second journal plus a receipt persisted in playerdata, so a restart between inventory delivery and database finalization can be resolved without delivering the same entries twice. MySQL stores grave metadata, payloads, audit records, and the single-writer server lease; the local journal remains the immediate authority while database projection is unavailable.

## Player flow

1. `PlayerDeathEvent` is snapshotted at `LOWEST` and evaluated at `HIGHEST` after other plugins have adjusted drops, retained items, XP, or keep-inventory.
2. The final drops are matched deterministically back to preferred inventory, armour, and offhand slots.
3. A reachable virtual-grave location is selected without loading or generating chunks. A recent safe location is preferred when the death position is hazardous; otherwise the grave becomes remote-only.
4. The payload and `PREPARED` capture journal are forced to disk. Only then are normal drops suppressed and the intended post-death inventory saved to playerdata.
5. The grave receives a stable readable command identifier in the form
   `<player>-<world>-<yyyyMMdd-HHmmss-SSS>`, is projected asynchronously to MySQL, and is rendered
   to nearby viewers. The timestamp uses the server timezone and millisecond precision, so normal
   command use never exposes the internal UUID.
6. The owner right-clicks the virtual interaction entity. Items restore to preferred slots where safe, merge into compatible stacks, then use empty slots. Nothing is overwritten or dropped.
7. If only part fits, the remaining entries stay in the grave and its active-server-time expiry continues.
8. A fully claimed or expired grave disappears with the configured effect. Expired payloads remain available for the configured support window, after which a lease-owned bounded retention sweep removes payload and metadata while preserving the audit record. Corrupt graves are never automatically purged.

## Commands and permissions

Root commands: `/grave` and `/graves`.

All player commands require `serverfeatures.feature.graveyard.use`. Subcommands additionally require their dedicated permission.

| Syntax | Permission | Behaviour |
|---|---|---|
| `/grave` | `.use` | Shows the newest grave owned by the player. |
| `/grave list` | `.command.list` | Lists the player's active, partial, remote, orphaned, or pending-delivery graves. |
| `/grave info <id>` | `.command.info` | Shows owner, server, world, coordinates, status, remaining active time, item-entry count, and XP. |
| `/grave locate <id>` | `.command.locate` | Shows the grave location. Physical graves still require a valid nearby interaction to claim. |
| `/grave track <id>` | `.command.track` | Selects a grave for periodic actionbar distance and remaining-time feedback. |
| `/grave track off` | `.command.track` | Stops grave tracking. |
| `/grave claim <id>` | `.command.remoteclaim` | Claims graves marked `REMOTE_ONLY`, `ORPHANED_WORLD`, or `DELIVERY_PENDING`. |
| `/grave admin list <player>` | `.admin.list` | Lists known graves for an online or resolvable player. |
| `/grave admin info <id>` | `.admin.inspect` | Shows detailed grave diagnostics. |
| `/grave admin teleport <id>` | `.admin.teleport` | Teleports a player sender to the grave's resolved world location. |
| `/grave admin relocate <id>` | `.admin.relocate` | Relocates the grave to the executing player's safe location and rotates packet identity. |
| `/grave admin deliver <id>` | `.admin.deliver` | Delivers to the owner. If the owner is offline, marks durable pending delivery; staff never receive the payload. |
| `/grave admin expire <id>` | `.admin.expire` | Expires a claimable grave while retaining its payload. |
| `/grave admin restore <id>` | `.admin.restore` | Restores an expired/orphaned grave with a fresh configured lifetime. |
| `/grave admin purge <id> confirm` | `.admin.purge` | Permanently purges the grave through a guarded administrative action. |
| `/grave admin diagnostics` | `.admin.diagnostics` | Shows lease, runtime cache, journal backlog, pending claim, and rendered-pair counts. |

Permission nodes:

```text
serverfeatures.feature.graveyard.use
serverfeatures.feature.graveyard.command.list
serverfeatures.feature.graveyard.command.info
serverfeatures.feature.graveyard.command.locate
serverfeatures.feature.graveyard.command.track
serverfeatures.feature.graveyard.command.remoteclaim
serverfeatures.feature.graveyard.keepinventory
serverfeatures.feature.graveyard.admin
serverfeatures.feature.graveyard.admin.list
serverfeatures.feature.graveyard.admin.inspect
serverfeatures.feature.graveyard.admin.teleport
serverfeatures.feature.graveyard.admin.relocate
serverfeatures.feature.graveyard.admin.deliver
serverfeatures.feature.graveyard.admin.expire
serverfeatures.feature.graveyard.admin.restore
serverfeatures.feature.graveyard.admin.purge
serverfeatures.feature.graveyard.admin.diagnostics
```

`serverfeatures.feature.graveyard.admin` implies every administrative child command. `keepinventory` is checked independently and is not implied by the administrator node. It retains inventory and level, creates no grave, and suppresses normal drops.

## Configuration reference

File: `plugins/ServerFeatures/features/Graveyard/config.yml`.

| Key | Default | Behaviour |
|---|---:|---|
| `enabled` | `false` | Enables feature initialization. |
| `mode` | `ACTIVE` | `ACTIVE` mutates deaths; `OBSERVE` validates and logs captures without changing the event or creating graves. |
| `identity.server_id` | global `server_name` | Stable backend identity used in persistence and the writer lease. |
| `identity.inventory_scope` | server name | Prevents delivery into an unrelated gamemode inventory. |
| `identity.lease_heartbeat` | `5s` | Single-writer lease renewal interval. |
| `identity.lease_timeout` | `20s` | Lease freshness window. Loss switches mutation paths to fail-closed/read-only behaviour. |
| `storage.journal.maximum_record_bytes` | `12000000` | Maximum local capture or claim record size. |
| `storage.payload.maximum_entries` | `64` | Defensive payload item-entry limit. |
| `storage.payload.maximum_item_bytes` | `2097152` | Maximum serialized bytes for one item entry. |
| `storage.payload.maximum_total_bytes` | `8388608` | Maximum encoded payload size. |
| `storage.retention.expired` | `7d` | Support window for expired payloads before automatic permanent cleanup. |
| `storage.retention.claimed` | `24h` | Retention for claimed, administratively recovered, or already-purged metadata before row cleanup. |
| `storage.retention.purge_interval` | `10m` | Interval between bounded retention sweeps; minimum one minute. |
| `storage.retention.purge_batch_size` | `100` | Maximum records removed per transaction; full batches are drained gradually. |
| `lifetime.duration` | `30m` | Lifetime measured by the plugin-scoped active-server clock, not wall time. |
| `eligibility.disabled_worlds` | empty | Case-insensitive world names or namespaced world keys excluded from Graveyard. |
| `eligibility.disabled_gamemodes` | `CREATIVE`, `SPECTATOR` | Game modes that retain normal death behaviour. |
| `experience.recovery_percentage` | `50` | Percentage of the final event dropped-XP value captured, clamped to `0..100`. |
| `placement.horizontal_search_radius` | `8` | Loaded-block horizontal candidate radius. |
| `placement.vertical_search_below` | `4` | Candidate search below death position. |
| `placement.vertical_search_above` | `6` | Candidate search above death position. |
| `placement.last_safe_location_max_age` | `30s` | Maximum age for a tracked safe-location fallback. |
| `render.spawn_distance` | `48` | Inclusive spawn distance. |
| `render.despawn_distance` | `56` | Despawn distance; clamped not lower than spawn distance to provide hysteresis. |
| `render.reconciliation_interval_ticks` | `20` | Central viewer reconciliation period; no task is created per grave. |
| `render.spawn_settle_delay_ticks` | `2` | Delay before rebuilding a viewer after world/teleport transitions. |
| `render.max_rendered_per_viewer` | `64` | Visual cap only; own and nearest graves are prioritized and storage is unaffected. |
| `render.base.material` | `POLISHED_BLACKSTONE_BRICK_SLAB` | Virtual base block-display material. |
| `render.headstone.material` | `POLISHED_BLACKSTONE_BRICK_WALL` | Virtual headstone block-display material. |
| `render.glow.owner_rgb` | `55FFFF` | Per-viewer owner glow override. |
| `render.glow.staff_rgb` | `FFD700` | Staff glow override. |
| `render.glow.other_rgb` | `00AAAA` | Ordinary-viewer glow override. |
| `interaction.maximum_distance` | `4.5` | Server-side interaction-distance limit. |
| `interaction.require_line_of_sight` | `true` | Rechecks line of sight before a packet interaction can claim. |
| `claim.partial_claims` | `true` | Keeps overflow entries in the grave. When disabled, an insufficient inventory returns `NOTHING_FIT`. |
| `particles.claim.type` | `SCULK_SOUL` | Claim particle. Invalid names fall back independently. |
| `particles.expiry.type` | `SCULK_SOUL` | Expiry particle. |
| `sounds.claim.sound` | `BLOCK_RESPAWN_ANCHOR_CHARGE` | Claim sound resolved through the Bukkit sound registry. |
| `sounds.expiry.sound` | `PARTICLE_SOUL_ESCAPE` | Expiry sound. |

Durations accept positive raw milliseconds or `ms`, `s`, `m`, `h`, and `d` suffixes. Invalid or non-positive values fall back to the documented default.

## Virtual grave and viewer lifecycle

A visual generation contains globally generated packet entity IDs and random UUIDs for:

- base `BLOCK_DISPLAY`;
- headstone `BLOCK_DISPLAY`;
- multiline `TEXT_DISPLAY`;
- invisible `INTERACTION` entity.

The renderer sends concrete `PacketWrapper<?>` instances through PacketEvents. Display and
interaction metadata is generated from unspawned Bukkit entity templates and converted by
PacketEvents, avoiding protocol-version-specific metadata indexes. Partial spawn failure triggers
best-effort destruction. Every viewer state records the visual generation and rendered timer
string. Relocation or hard rebuilding rotates the generation so delayed interactions and metadata
callbacks cannot affect the replacement.

The interaction packet listener consumes only IDs in Graveyard's active interaction index and schedules all Bukkit validation on the main thread. It rechecks connection state, generated identity, world, server-side distance, line of sight, claim state, inventory scope, ownership/staff permission, and operation reservation. Attack actions are ignored.

The spatial index is `world UUID -> packed chunk key -> grave IDs`. Reconciliation checks only nearby indexed chunks and does not load chunks. A grave is rendered only when its world resolves by both UUID and key and Paper reports the grave chunk as sent to that viewer.

Timer metadata is derived from active time and updated only when the displayed value changes. Text uses minute granularity above one hour, ten-second granularity from five minutes to one hour, and second granularity below five minutes.

## Active-server clock

`ServerActiveClock` is owned by the ServerFeatures plugin bootstrap, not by Graveyard. It uses monotonic process time plus an atomically replaced local checkpoint. The checkpoint is updated every five seconds and on plugin shutdown.

Consequences:

- ordinary feature reload does not reset grave time;
- server downtime does not consume grave lifetime;
- after a hard crash, at most one checkpoint interval is omitted, making a grave live slightly longer rather than expiring early;
- active clock values never depend on wall-clock corrections.

## Persistence

Connection: feature-owned `graveyardOrm`, MySQL, access policy `player_data_rw`.

Retention cleanup runs only under the active writer lease and never automatically removes `CORRUPT` graves.

Entities/tables (the feature does not read or migrate the former unprefixed development tables):

- `player_graveyard_graves`: lightweight metadata, location, state, expiry, payload revision/checksum, and optimistic operation token;
- `player_graveyard_payloads`: versioned payload BLOB separate from render/list metadata;
- `player_graveyard_audit`: administrative and claim state transitions;
- `player_graveyard_leases`: single-writer lease for `server_id + inventory_scope`.

The readable identifier is stored in `short_id` with sufficient room for owner, world, and the
creation timestamp. It is stable across relocation; `/grave info` and `/grave locate` report the
current coordinates separately. Existing unprefixed development tables can be removed by
operators after confirming they are no longer needed.

The owner UUID is always stored. Canonical DataRegistry identity can be resolved independently and is not required on the synchronous death path.

Payload format is a versioned binary envelope with stable entry UUIDs, preferred slot data, Paper raw item bytes, remaining XP, and SHA-256 checksum. Decode validates schema, counts, item sizes, total size, duplicate IDs, AIR/invalid entries, and checksum. A decode failure is not treated as an empty grave; the operation fails closed and is surfaced through diagnostics/logging.

## Capture journal protocol

1. Final death drops and XP are encoded.
2. `PREPARED` is written to an independent atomic journal file and forced to disk.
3. A capture receipt is placed in player PDC.
4. The intended post-death inventory/XP is applied; keep-inventory is set internally; event drops and XP are cleared.
5. `Player#saveData()` persists the ownership transfer.
6. The journal advances to `COMMITTED`.
7. MySQL projection is retried asynchronously until successful, then the journal is removed.

If preparation fails, Graveyard leaves the event untouched. A crash with `PREPARED` is resolved on owner join: a matching persisted receipt promotes it to committed; no receipt aborts it. A `COMMITTED` journal is loaded and rendered even while MySQL remains unavailable, but database-reserved mutations remain fail-closed until projection succeeds.

## Claim journal protocol

1. MySQL atomically reserves the grave with an operation UUID and expected claimable state.
2. The current payload is loaded and a deterministic transfer plan is calculated.
3. A `PREPARED` claim record stores the exact transferred count, XP, and remaining encoded payload.
4. The player receipt and inventory changes are saved to playerdata.
5. The journal advances to `PLAYER_APPLIED`.
6. MySQL finalizes the remaining payload and state using operation token and payload revision preconditions.
7. The receipt and journal are removed only after finalization.

A `PREPARED` claim is not immediately discarded at startup: the owner's playerdata receipt decides whether it was applied. This prevents the critical crash window after playerdata save but before the journal-state rewrite from duplicating items.

## Item restoration

The claim planner never calls APIs that may drop overflow. It works against a cloned inventory snapshot and applies the result only after durable preparation.

Order:

1. original preferred slot when valid and empty;
2. merge into compatible partial stacks;
3. original armour/offhand slot when valid and empty;
4. empty storage/hotbar slots;
5. retain remaining quantity in the grave.

Existing items are never swapped or overwritten. XP is transferred once and removed from the remaining payload independently of item capacity.

## Placement and worlds

Candidate order is exact death position, vertical correction, expanding loaded-block rings, recent safe location, and remote-only fallback. Candidates require a solid collision surface, adequate body/headroom, a nearby standable interaction position, world-border validity, and rejection of lava, fire, cactus, magma, powder snow, portals, and void exposure.

Graves persist both world UUID and namespaced key. A matching key with a different UUID is treated as a world reset, not as the original world. Missing/reset worlds become `ORPHANED_WORLD`, pause their remaining time, disappear from spatial rendering, and permit owner remote recovery.

## Staff delivery

Staff never receive another player's grave contents. `admin deliver` either performs the normal claim against the online owner or changes the grave to `DELIVERY_PENDING`. Pending delivery pauses normal interaction, is retried when the owner joins the same inventory scope, and can also be requested manually by the owner through `/grave claim <id>` after freeing inventory space. All state-changing staff actions append an audit row with actor, grave, old/new state, and payload counts.

## Failure modes

- **Journal unavailable:** death event is left unchanged.
- **Database unavailable during capture:** committed local record remains authoritative and projection retries.
- **Database unavailable during claim:** operation fails before player delivery, or a previously player-applied operation stays journal-backed for deterministic recovery.
- **Lease unavailable/lost:** new mutation paths fail closed; visuals and diagnostic reads may continue.
- **Payload invalid:** no item is delivered and the error is logged; operators retain the stored record for investigation.
- **World unavailable/reset:** grave becomes non-rendered orphaned recovery rather than appearing in a replacement world.
- **Inventory full:** claim remains unchanged or partial; no item entity is spawned.

## Messages

Feature messages include creation, remote creation, storage fallback, keep-inventory, claim success/partial/full, inventory full, owner/claim-state errors, location/tracking output, pending delivery, staff operations, list/info formatting, and diagnostics. Common variables are:

```text
<grave_id> <player> <server> <world> <x> <y> <z>
<distance> <remaining> <item_count> <xp> <state> <actor>
```

## API

Published service: `nl.hauntedmc.serverfeatures.api.graveyard.GraveyardService`.

It exposes immutable snapshots and asynchronous claim requests by grave UUID/short ID. It never exposes mutable item payloads, Hibernate entities, packet IDs, or journal records.

## Shutdown

Shutdown stops captures and interactions, destroys every visible packet entity, clears interaction mappings and viewer state, releases the lease, and then lets the feature lifecycle close tasks, listeners, command, API, and data scopes. No world cleanup is required because Graveyard never creates a real world entity or block.

## Test coverage and acceptance

Automated tests cover duration parsing, spatial reindexing/removal, capture and claim journal round-trips, corrupt-record quarantine/token preservation, payload checksum/schema validation, retained-item reconstruction, partial/all-or-nothing claim planning, stack limits, and paused-state resume behaviour. The production acceptance pass should additionally exercise ordinary/void/lava deaths, retained and vanishing items, full-inventory partial claims, duplicate interaction packets, restart at each journal boundary, database outage, world reset, staff online/offline delivery, vanished owners, feature reload, and absence of ghost displays.
