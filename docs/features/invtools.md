# InvTools

InvTools gives staff safe inventory administration under one Brigadier command tree:

- `/inv inventory open <name>` shows main storage, hotbar, armor, and offhand as visually
  separate sections.
- `/inv enderchest open <name>` shows all 27 ender chest slots.
- `/inv inventory clear <name>` safely clears the player's inventory, armor, and offhand.
- `/inv enderchest clear <name>` safely clears all 27 ender chest slots.

The feature is disabled by default. Enable `InvTools` through the normal feature command or set
`enabled: true` in `plugins/ServerFeatures/features/InvTools/config.yml`. Item-NBT-API is shaded into
ServerFeatures; it is not a separate server plugin dependency.

## Offline identity resolution

InvTools treats player identity and local playerdata ownership as two separate questions:

1. DataRegistry resolves the requested name to the network's canonical, forwarded Minecraft UUID.
   Its active cache is used as a fast path and its persisted player directory is queried for known
   offline players.
2. InvTools targets `<level>/players/data/<uuid>.dat` on this exact server and requires it to be a
   regular, non-symbolic file before reading or changing anything.

Paper 26.1 changed the world storage format. Player records are now below `players/data` in the level
root; the removed pre-26.1 `<level>/playerdata` directory is deliberately not used. The configured
level directory comes from Paper, so this works with any default world name instead of assuming
`world`.

Paper can expose a player as offline and fire the quit event before the final logout save has become
visible on disk. An authoritative UUID from a real connection or DataRegistry is therefore allowed to
reach the bounded playerdata-appearance retry even when the file is momentarily absent. Identity
resolution must not reject that UUID first, because doing so would make the logout retry unreachable.
A player who is merely known elsewhere on the network still fails safely when no local file appears.

This avoids Paper's cache-only `getOfflinePlayerIfCached` limitation and avoids deriving offline-mode
UUIDs on a Velocity backend. It also prevents a player known elsewhere on the network from being
mistaken for a player with data on this server.

For playerdata created before DataRegistry was authoritative, InvTools retains a conservative local
fallback. It can use an identity observed during a real connection, the player's
`bukkit.lastKnownName` metadata, or a matching local entry in Paper's `usercache.json`. An existing
legacy UUID file inside the current `players/data` directory takes precedence over a canonical UUID
whose file has not appeared, preserving access across UUID-mode migrations. A DataRegistry query
failure is reported as a load failure rather than incorrectly claiming that the player never joined.

InvTools never performs a blocking Mojang profile lookup and never constructs a fake offline
`Player` entity. Offline inventory access is direct, narrowly scoped playerdata I/O.

## Permissions

- `serverfeatures.feature.invtools.command.inventory.open.inspect` opens a player inventory
  read-only.
- `serverfeatures.feature.invtools.command.inventory.open.edit` additionally permits editing it.
- `serverfeatures.feature.invtools.command.inventory.clear` permits clearing it.
- `serverfeatures.feature.invtools.command.enderchest.open.inspect` opens an ender chest
  read-only.
- `serverfeatures.feature.invtools.command.enderchest.open.edit` additionally permits editing it.
- `serverfeatures.feature.invtools.command.enderchest.clear` permits clearing it.

An edit permission does not grant the matching open permission, and clear permissions are separate
from both. Neither permission is granted by default.

## Session behavior

Online inventories use Paper's inventory API and remain live. The GUI refreshes from the target
every `online_sync_interval_ticks` (five ticks by default), and staff edits are applied to the
target immediately. Only one editor may modify an online target at a time; additional staff may
still inspect that target. If the target disconnects, all live views close because the online
inventory object is no longer authoritative. Before every online edit, InvTools compares the
clicked slot with the target's current inventory; if it changed since the GUI was rendered, the
view refreshes and requires a second click instead of applying a stale mutation.

Shift-click works in both directions for online inventory and ender chest views. InvTools cancels
Bukkit's native bulk routing and performs the transfer itself so decorative GUI slots can never
receive real items. Transfers merge compatible stacks first, use main inventory storage before the
hotbar, and never unexpectedly auto-equip armor or offhand items.

The main storage and hotbar are deliberately distinct in the GUI and audit log. On disk they retain
Minecraft's canonical slot IDs: `0–8` for the hotbar and `9–35` for main storage. Avoiding a second
internal numbering scheme keeps online and offline mutations equivalent and removes a class of
conversion bugs.

Offline access reads `<level>/players/data/<uuid>.dat` away from the server thread. Only one offline
session per target is allowed, including read-only sessions. A write:

- changes only the selected inventory or ender chest tags;
- verifies a SHA-256 revision before creating the recovery backup and again immediately before
  replacing the file;
- writes through a same-directory temporary file, requires an atomic move, and fsyncs it where the
  file system permits;
- retains the exact pre-write file as
  `<level>/players/data/<uuid>.dat.invtools-backup` for recovery;
- preserves unrelated player NBT and POSIX ownership, group, and permissions.

At most `max_offline_sessions` offline sessions are active at once (four by default). New requests
fail fast while that limit is reached instead of occupying Paper's shared asynchronous workers.
Offline clear uses the same limit, target reservation, revision checks, atomic replacement, recovery
backup, and login barrier as offline editing. An inventory with an active online editor is never
cleared until that editor has closed their session.

Offline direct cursor editing remains isolated from the staff member's own inventory. This keeps the
virtual target cursor independent from Bukkit's real cursor and prevents ordinary bottom-inventory,
number-key, drag, and destructive Q actions from bypassing the playerdata transaction. If the GUI
closes with a target item on that virtual cursor, the item is inserted back into the target snapshot
before saving.

Shift-click is the supported way to move items between the staff inventory and an offline target.
Each staff-side change is journaled together with the detached target snapshot. Closing the view
briefly locks the transfer session while the atomic playerdata write settles:

- a successful save commits both sides of the transfer;
- a conflict, failed save, or discarded session replays the inverse staff-inventory operations;
- items returned during rollback are merged safely, and a remainder is dropped beside the staff
  member rather than silently deleted if their inventory has become full;
- the staff member cannot move, consume, drop, swap, or pick up transfer-sensitive items while that
  final settlement is pending.

This prevents receiving an item when the offline target file was never updated, and prevents losing
an item when a transfer into the target fails to persist.

When an offline target starts logging in, pre-login waits up to
`offline_io_timeout_seconds` (ten seconds by default) for pending changes to commit before the
server loads that playerdata. A failed or timed-out commit rejects that login once with a retry
message, preventing stale data from overwriting staff changes. The timeout is clamped to 30 seconds
to protect Paper's login threads. Configuration and join listeners guard a race that began before
the offline reservation. Paper's initial-configuration event catches this before player
construction where possible, with join as a final guard; both close the view and discard
uncommitted GUI state instead of writing over data already being loaded.
An explicit login fence rejects new offline opens from pre-login until Paper has exposed the
player as online, closing the remaining transition gap.

Feature or server shutdown first closes visible sessions and settles their saves. It then closes the
playerdata operation gate: queued operations that have not started are rejected, while every read,
migration, crash recovery, clear, or save that already crossed the gate is allowed to reach a real
terminal state before migration login protection is removed. This prevents a queued legacy-data load
from beginning after feature disable and rewriting playerdata without its listener protections.

## Legacy playerdata migration

InvTools has one current-schema inventory reader and writer. It does not hand-parse old layouts.
Instead, an older positive `DataVersion` is upgraded with the same full Paper
`DataFixTypes.PLAYER` data fixer used when Paper loads a real player. A record newer than the running
Paper build is rejected because data fixing is forward-only. Missing, non-integer, malformed, or
non-positive versions are also rejected.

During feature initialization, InvTools checks that Item-NBT-API's target DataVersion exactly matches
Paper's runtime DataVersion and resolves the exact public `CompoundTag` overload of Paper's PLAYER
data fixer. The feature fails to initialize if either contract is unavailable; it does not wait until
a staff command encounters legacy data.

Migration is part of the offline open or clear operation and runs away from the server thread under
the target's exclusive playerdata lock:

1. The source file is read, bounded, parsed, UUID-checked, and hashed.
2. The exact compressed bytes are copied to a same-directory
   `<uuid>.dat.invtools-migration-backup` through a temporary file. The backup is fsynced, atomically
   installed, reread, and hash-verified before Paper's converter is called.
3. Paper converts a detached NBT root to the running `DataVersion`.
4. InvTools validates the converted UUID, version, inventory lists, equipment compounds, item
   decoding, slot uniqueness, and legal stack sizes before touching the source file.
5. The converted root is written to another same-directory temporary file, fsynced, reread, and
   validated again.
6. The source SHA-256 is checked immediately before the converted file is atomically installed.
7. The committed file is reread and compared with the validated temporary bytes. Only after this
   succeeds is the temporary migration backup deleted.

A conversion or validation failure before replacement leaves the original bytes untouched. A
failure after replacement restores the exact backup through another fsynced atomic replacement and
verifies the restored hash. If another process makes a different parseable change before commit or
before rollback, InvTools does not overwrite it; it fails closed and retains the migration backup for
diagnosis. If both conversion and automatic recovery fail, the backup is retained and the server log
identifies its exact path.

A leftover migration backup is reconciled on the next InvTools access. A valid current target means
the prior commit succeeded and only cleanup was interrupted, so the stale backup is removed. A
missing or unreadable target is restored from the verified backup. Two different, parseable files are
considered ambiguous and are both retained instead of guessing which one is authoritative. Backup-only
state is recognized by the service preflight, so recovery remains reachable even if the primary file
is absent.

The command user receives in-game progress for detection, backup completion, conversion, rollback,
success, and terminal failure. The migration-specific UUID fence is active for the actual
playerdata operation, including waiting for the per-target lock, conversion, recovery, and saving.
The ordinary InvTools reservation and login fences protect the preceding identity-resolution handoff
without leaving a player blocked when an offline request is rejected before disk work starts.
`AsyncPlayerPreLoginEvent` checks the migration fence before Paper reads playerdata, and Paper's
initial-configuration event checks it again before player construction to cover a connection that
started just before the operation.

The temporary migration backup is distinct from `<uuid>.dat.invtools-backup`, which is the retained
recovery copy for an intentional inventory edit. If migration succeeded but the temporary backup
cannot be deleted because of a filesystem problem, the converted file remains verified and the
staff member is warned; the next access retries safe cleanup.

## Operational notes

- Keep the server's normal playerdata backups; direct offline editing is deliberately conservative,
  but it is still an administrative data mutation.
- Failed identity resolution and temporarily missing logout files emit an InvTools warning containing
  the requested name, resolved UUID where available, absolute playerdata directory, exact expected
  `.dat` path, and directory/file type and readability state. A remaining production mismatch is
  therefore diagnosable without enabling a separate debug mode.
- A save conflict or malformed/oversized playerdata file is rejected and logged. It is never
  overwritten optimistically.
- If a playerdata file contains a UUID identity tag, that identity must match its filename; copied or
  swapped files are rejected before inspection, migration, or mutation.
- Compressed and decompressed playerdata sizes are bounded before NBT parsing to reject pathological
  files without exhausting the server heap.
- Administrative mutations are logged by default with a session ID, actor, target, source,
  inventory section, canonical slot, before/after material counts, and an outcome. Online edits are
  recorded as applied immediately. Offline edits are recorded as pending and receive exactly one
  terminal saved, conflict, failed, or discarded save entry with the same session ID. Set
  `audit_edits: false` only if another audit system records the same actions and outcomes.
