# InvTools

InvTools gives staff a safe GUI for inspecting or editing a player's inventory and ender chest:

- `/invsee <name>` shows main storage, hotbar, armor, and offhand as visually separate sections.
- `/endersee <name>` shows all 27 ender chest slots.

The feature is disabled by default. Enable `InvTools` through the normal feature command or set
`enabled: true` in `plugins/ServerFeatures/features/InvTools/config.yml`. Offline lookup uses only
Paper's local player cache to map a name to UUID, then requires that UUID's `playerdata` file in this
server's primary world. It does not use DataRegistry or any global player registry. Item-NBT-API is
shaded into ServerFeatures; it is not a separate server plugin dependency.

## Permissions

- `serverfeatures.feature.invtools.command.invsee.inspect` opens `/invsee` read-only.
- `serverfeatures.feature.invtools.command.invsee.edit` additionally permits editing `/invsee`.
- `serverfeatures.feature.invtools.command.endersee.inspect` opens `/endersee` read-only.
- `serverfeatures.feature.invtools.command.endersee.edit` additionally permits editing `/endersee`.

An edit permission does not grant the matching inspect permission; grant both for that command.
Neither permission is granted by default.

## Session behavior

Online inventories use Paper's inventory API and remain live. The GUI refreshes from the target
every `online_sync_interval_ticks` (five ticks by default), and staff edits are applied to the
target immediately. Only one editor may modify an online target at a time; additional staff may
still inspect that target. If the target disconnects, all live views close because the online
inventory object is no longer authoritative.

The main storage and hotbar are deliberately distinct in the GUI and audit log. On disk they retain
Minecraft's canonical slot IDs: `0–8` for the hotbar and `9–35` for main storage. Avoiding a second
internal numbering scheme keeps online and offline mutations equivalent and removes a class of
conversion bugs.

Offline access reads the primary world's `playerdata/<uuid>.dat` file away from the server thread.
Only one offline session per target is allowed, including read-only sessions. A write:

- changes only the selected inventory or ender chest tags;
- verifies a SHA-256 revision before replacing the file;
- writes through a same-directory temporary file, requires an atomic move, and fsyncs it where the
  file system permits;
- retains the exact pre-write file as `playerdata/<uuid>.dat.invtools-backup` for recovery;
- preserves unrelated player NBT and POSIX ownership, group, and permissions.

At most `max_offline_sessions` offline sessions are active at once (four by default). New requests
fail fast while that limit is reached instead of occupying Paper's shared asynchronous workers.

Offline editing is isolated from the staff member's own inventory. The bottom inventory, shift
transfer, number-key swaps, drags, and destructive Q shortcuts are blocked. Items may be safely
rearranged inside the target GUI. Read-only offline inspection does not isolate the staff cursor or
block ordinary interaction with the staff inventory. If an edit GUI closes with a target item on the
cursor, that item is inserted back into the target snapshot before saving and the real cursor is
cleared. This prevents duplication or loss when a write conflicts, fails, or overlaps a login.

When an offline target starts logging in, pre-login waits up to
`offline_io_timeout_seconds` (ten seconds by default) for pending changes to commit before the
server loads that playerdata. A failed or timed-out commit rejects that login once with a retry
message, preventing stale data from overwriting staff changes. The timeout is clamped to 30 seconds
to protect Paper's login threads. Configuration and join listeners guard a race that began before
the offline reservation. Paper's initial-configuration event catches this before player
construction where possible, with join as a final guard; both close the view and discard
uncommitted GUI state instead of writing over data already being loaded.

Feature or server shutdown closes every visible and already-saving offline session. It waits for
each in-flight atomic playerdata write to reach its actual terminal outcome before the lifecycle
task manager is allowed to cancel remaining feature work.

Offline access is limited to playerdata from this exact Paper build. Older or future playerdata is
rejected before its inventory is decoded; InvTools has one current-schema reader and writer, with no
legacy compatibility paths. The exact DataVersion is obtained from the running Paper data fixer rather
than maintained as a hard-coded version number.

## Operational notes

- Keep the server's normal playerdata backups; direct offline editing is deliberately conservative,
  but it is still an administrative data mutation.
- A save conflict or malformed/oversized playerdata file is rejected and logged. It is never
  overwritten optimistically.
- If a current playerdata file contains a UUID identity tag, that identity must match its filename;
  copied or swapped files are rejected before inspection or mutation.
- Compressed and decompressed playerdata sizes are bounded before NBT parsing to reject compression
  bombs and pathological files without exhausting the server heap.
- Administrative mutations are logged by default with a session ID, actor, target, source,
  inventory section, canonical slot, before/after material counts, and an outcome. Online edits are
  recorded as applied immediately. Offline edits are recorded as pending and receive exactly one
  terminal saved, conflict, failed, or discarded save entry with the same session ID. Set
  `audit_edits: false` only if another audit system records the same actions and outcomes.
