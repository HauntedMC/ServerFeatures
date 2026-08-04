# Graveyard

> Paper · Feature name `Graveyard` · feature package `features.graveyard` · disabled by default

Graveyard replaces physical death chests with durable, packet-only graves. A successful capture transfers the final death-event drops and a configurable share of dropped experience into a checksummed payload. Nearby clients receive virtual block displays, a text display and an invisible interaction entity; no block or Bukkit entity is placed in the world.

The implementation is fail-safe. Death drops are changed only after a local `PREPARED` journal record has been synchronously forced to disk. Claims use a second journal plus a receipt persisted in playerdata, so a restart between inventory delivery and database finalization cannot deliver the same entries twice.

## Player flow

1. `PlayerDeathEvent` is snapshotted at `LOWEST` and evaluated at `HIGHEST` after other plugins have adjusted drops, retained items, XP or keep-inventory.
2. The final drops are matched deterministically back to preferred inventory, armour and offhand slots.
3. A reachable virtual-grave location is selected without loading or generating chunks. A recent safe location is preferred when the death position is hazardous; otherwise the grave becomes remote-only.
4. The payload and `PREPARED` capture journal are forced to disk. Only then are normal drops suppressed and the intended post-death inventory saved to playerdata.
5. New graves receive a readable identifier in the exact form `<player>-<HH:mm:ss>`, using the server timezone. The internal UUID remains the durable unique identity.
6. The owner right- or left-clicks the virtual interaction entity. Items return to preferred slots where safe, merge into compatible stacks and then use empty inventory slots. Nothing is overwritten or dropped.
7. If only part fits, the remaining entries stay in the grave and its active-server-time expiry continues.
8. A fully claimed or expired grave disappears with its configured effect. Claimed and expired records are retained for at most the configured support window before bounded cleanup removes payload and metadata while preserving audit history.

Existing graves keep the identifier stored with them. Only newly created graves use the compact `<player>-<HH:mm:ss>` format.

## Commands and permissions

The only root command is `/grave`. The former `/graves` alias is not registered.

Grave identifiers contain colons and are accepted directly without quotes, for example:

```text
/grave locate RemyMine-10:27:15
```

| Syntax | Permission | Behaviour |
|---|---|---|
| `/grave` | `.use` | Shows the newest grave owned by the player. |
| `/grave list` | `.command.list` | Lists the player's active, partial, remote, orphaned or pending-delivery graves. |
| `/grave info <id>` | `.command.info` | Shows owner, server, world, coordinates, status, remaining time, item-entry count and XP. |
| `/grave locate <id>` | `.command.locate` | Shows the grave location. A physical grave still requires a valid nearby interaction to claim. |
| `/grave track <id>` | `.command.track` | Tracks distance and remaining time in the action bar. |
| `/grave track off` | `.command.track` | Stops grave tracking. |
| `/grave claim <id>` | `.command.remoteclaim` | Claims a `REMOTE_ONLY`, `ORPHANED_WORLD` or `DELIVERY_PENDING` grave. |
| `/grave admin list <player>` | `.admin.list` | Lists known recoverable graves for a player. |
| `/grave admin info <id>` | `.admin.inspect` | Shows detailed grave diagnostics. |
| `/grave admin teleport <id>` | `.admin.teleport` | Teleports the executing player to the grave. |
| `/grave admin relocate <id>` | `.admin.relocate` | Relocates a grave to the executing player's safe location. |
| `/grave admin deliver <id>` | `.admin.deliver` | Delivers to the owner or queues durable delivery when the owner is offline. |
| `/grave admin expire <id>` | `.admin.expire` | Expires a recoverable grave while retaining its payload. |
| `/grave admin restore <id>` | `.admin.restore` | Restores an expired or orphaned grave with a fresh lifetime. |
| `/grave admin purge <id> confirm` | `.admin.purge` | Permanently purges a grave through a guarded action. |
| `/grave admin diagnostics` | `.admin.diagnostics` | Shows lease, runtime cache, journal backlog, pending claims and rendered-pair counts. |

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

`serverfeatures.feature.graveyard.admin` implies every administrative child command. `keepinventory` is checked independently and is not implied by the administrator node. It retains inventory and level, creates no grave and suppresses normal drops.

## Configuration reference

File: `plugins/ServerFeatures/features/Graveyard/config.yml`.

| Key | Default | Behaviour |
|---|---:|---|
| `enabled` | `false` | Enables feature initialization. |
| `mode` | `ACTIVE` | `ACTIVE` mutates deaths; `OBSERVE` validates captures without changing the event. |
| `identity.server_id` | global `server_name` | Stable backend identity used in persistence and the writer lease. |
| `identity.inventory_scope` | server name | Prevents delivery into an unrelated gamemode inventory. |
| `identity.lease_heartbeat` | `5s` | Single-writer lease renewal interval. |
| `identity.lease_timeout` | `20s` | Lease freshness window. Lease loss makes mutations fail closed. |
| `storage.journal.maximum_record_bytes` | `12000000` | Maximum local capture or claim record size. |
| `storage.payload.maximum_entries` | `64` | Defensive payload item-entry limit. |
| `storage.payload.maximum_item_bytes` | `2097152` | Maximum serialized bytes for one item entry. |
| `storage.payload.maximum_total_bytes` | `8388608` | Maximum encoded payload size. |
| `storage.retention.expired` | `1h` | Maximum support window for expired payloads before cleanup. Values above one hour are capped. |
| `storage.retention.claimed` | `1h` | Maximum retention for claimed or administratively completed records. Values above one hour are capped. |
| `storage.retention.purge_interval` | `10m` | Interval between bounded retention sweeps; minimum one minute. |
| `storage.retention.purge_batch_size` | `100` | Maximum records removed per transaction. |
| `lifetime.duration` | `10m` | Grave lifetime measured by the plugin active-server clock, not wall time. |
| `eligibility.disabled_worlds` | empty | Case-insensitive world names or namespaced keys excluded from Graveyard. |
| `eligibility.disabled_gamemodes` | `CREATIVE`, `SPECTATOR` | Game modes that keep normal death behaviour. |
| `experience.recovery_percentage` | `50` | Percentage of final dropped XP captured, clamped to `0..100`. |
| `placement.horizontal_search_radius` | `8` | Loaded-block horizontal candidate radius. |
| `placement.vertical_search_below` | `4` | Candidate search below the death position. |
| `placement.vertical_search_above` | `6` | Candidate search above the death position. |
| `placement.last_safe_location_max_age` | `30s` | Maximum age of a tracked safe-location fallback. |
| `render.spawn_distance` | `48` | Inclusive visual spawn distance. |
| `render.despawn_distance` | `56` | Visual despawn distance with hysteresis. |
| `render.reconciliation_interval_ticks` | `20` | Central viewer and timer refresh interval. The default is once per second. |
| `render.spawn_settle_delay_ticks` | `2` | Delay before rebuilding a viewer after world or teleport transitions. |
| `render.max_rendered_per_viewer` | `64` | Visual cap; own and nearest graves are prioritized. |
| `render.base.material` | `DARK_OAK_SLAB` | Virtual grave-bed material. |
| `render.headstone.material` | `DARK_OAK_PLANKS` | Virtual memorial and cross material. |
| `render.glow.owner_rgb` | `55FFFF` | Owner glow override. |
| `render.glow.staff_rgb` | `FFD700` | Staff glow override. |
| `render.glow.other_rgb` | `00AAAA` | Ordinary-viewer glow override. |
| `interaction.maximum_distance` | `4.5` | Server-side interaction-distance limit. |
| `interaction.require_line_of_sight` | `true` | Requires line of sight before a packet interaction can claim. |
| `claim.partial_claims` | `true` | Keeps overflow entries in the grave instead of dropping or overwriting them. |
| `particles.claim.type` | `SCULK_SOUL` | Claim particle. |
| `particles.expiry.type` | `SCULK_SOUL` | Expiry particle. |
| `sounds.claim.sound` | `BLOCK_RESPAWN_ANCHOR_CHARGE` | Claim sound; enum-style and namespaced registry keys are supported. |
| `sounds.expiry.sound` | `PARTICLE_SOUL_ESCAPE` | Expiry sound; enum-style and namespaced registry keys are supported. |

Durations accept positive raw milliseconds or `ms`, `s`, `m`, `h` and `d` suffixes. Invalid or non-positive values fall back to the defaults above.

Generated defaults apply to new or missing keys. An existing explicit `lifetime.duration` remains authoritative until it is changed to `10m`. Claimed and expired retention are capped at `1h` even when an existing configuration contains a larger value.

## Rendering and timer lifecycle

A visual generation contains globally allocated packet entity IDs and random UUIDs for:

- grave-bed `BLOCK_DISPLAY`;
- vertical memorial `BLOCK_DISPLAY`;
- horizontal crossbar `BLOCK_DISPLAY`;
- multiline `TEXT_DISPLAY`;
- invisible `INTERACTION` entity.

Every visual is packet-only. Partial spawn failure triggers best-effort destruction, and all five packet entities are removed together on claim, expiry, relocation, reload, viewer transition and shutdown.

Timer metadata is derived from active-server time and only sent when the displayed value changes. With the default 20-tick reconciliation interval, the complete ten-minute countdown updates once per second rather than rounding to ten-second steps.

## Active-server clock

`ServerActiveClock` is owned by the ServerFeatures plugin bootstrap. It uses monotonic process time plus an atomically replaced local checkpoint.

Consequences:

- ordinary feature reload does not reset grave time;
- server downtime does not consume grave lifetime;
- after a hard crash, at most one checkpoint interval is omitted, making a grave live slightly longer rather than expiring early;
- wall-clock corrections do not alter active grave lifetime.

## Persistence and recovery

Connection: feature-owned `graveyardOrm`, MySQL, access policy `player_data_rw`.

Tables:

- `player_graveyard_graves`: metadata, readable ID, locations, state, expiry and operation token;
- `player_graveyard_payloads`: versioned checksummed item payload;
- `player_graveyard_audit`: administrative and claim state transitions;
- `player_graveyard_leases`: single-writer lease for `server_id + inventory_scope`.

The readable `<player>-<HH:mm:ss>` identifier is stable across relocation. The internal grave UUID is used for durable identity, journals and persistence.

Capture protocol:

1. Encode the final drops and XP.
2. Force a `PREPARED` capture record to disk.
3. Persist a player receipt.
4. Apply the intended post-death inventory and suppress normal drops.
5. Save playerdata.
6. Mark the journal `COMMITTED`.
7. Retry MySQL projection until successful and then remove the journal.

Claim protocol:

1. Reserve the grave in MySQL with an operation UUID.
2. Build a deterministic transfer plan without dropping overflow.
3. Force a `PREPARED` claim journal containing the exact result.
4. Apply inventory and XP, then save playerdata and its receipt.
5. Mark the journal `PLAYER_APPLIED`.
6. Finalize the remaining payload and state in MySQL.
7. Remove the receipt and journal only after durable finalization.

A corrupt or missing payload never becomes an empty successful claim. The operation fails closed and remains available for diagnostics.

## Placement, worlds and staff delivery

Placement checks the exact death position, vertical corrections, expanding loaded-block rings and a recent safe-location fallback. It does not load or generate chunks. Hazardous surfaces, blocked interaction positions, portals and void exposure are rejected. A grave that cannot be placed safely becomes remote-only.

World UUID and namespaced key are both persisted. A missing or replaced world becomes `ORPHANED_WORLD`, pauses remaining time and permits remote recovery rather than rendering in an unrelated world.

Staff never receive another player's contents. `admin deliver` either claims against the online owner or changes the grave to `DELIVERY_PENDING`. Pending delivery is retried when the owner joins the same inventory scope.

## Failure modes

- **Journal unavailable:** leave the death event untouched.
- **Database unavailable during capture:** retain the committed local journal and retry projection.
- **Database unavailable during claim:** fail before delivery or recover a previously player-applied operation from its receipt.
- **Lease unavailable or lost:** new mutation paths fail closed while diagnostic reads may continue.
- **Payload invalid:** deliver nothing and retain the record for investigation.
- **World unavailable or reset:** pause and expose remote recovery.
- **Inventory full:** leave the grave unchanged or partially claimed; never spawn overflow items.

## API and shutdown

Published service: `nl.hauntedmc.serverfeatures.api.graveyard.GraveyardService`.

It exposes immutable snapshots and asynchronous claim requests by grave UUID or readable identifier. Mutable payloads, Hibernate entities, packet IDs and journal records are not exposed.

Shutdown stops captures and interactions, destroys every visible packet entity, clears interaction mappings and viewer state, releases the writer lease and then lets the feature lifecycle close tasks, listeners, commands, API and data scopes. No world cleanup is required because Graveyard never creates a real block or entity.
