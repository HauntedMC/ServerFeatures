# InvTools

InvTools gives staff safe inventory administration under one Brigadier command tree:

- `/inv inventory open <name>` shows main storage, hotbar, armor, and offhand as visually separate sections.
- `/inv enderchest open <name>` shows all 27 ender chest slots.
- `/inv inventory clear <name>` safely clears the player's inventory, armor, and offhand.
- `/inv enderchest clear <name>` safely clears all 27 ender chest slots.

The feature is disabled by default. Enable `InvTools` through the normal feature command or set `enabled: true` in `plugins/ServerFeatures/features/InvTools/config.yml`. Item-NBT-API is shaded into ServerFeatures; it is not a separate server plugin dependency.

## Offline identity resolution

InvTools treats player identity and local playerdata ownership as two separate questions:

1. DataRegistry resolves the requested name to the network's canonical, forwarded Minecraft UUID. Its active cache is used as a fast path and its persisted player directory is queried for known offline players.
2. InvTools targets `world/playerdata/<uuid>.dat` on this exact server and requires it to be a regular, non-symbolic file before reading or changing anything.

Paper can expose a player as offline and fire the quit event before the final logout save has become visible on disk. An authoritative UUID from a real connection or DataRegistry is therefore allowed to reach the bounded playerdata-appearance retry even when the file is momentarily absent. Identity resolution must not reject that UUID first, because doing so would make the logout retry unreachable. A player who is merely known elsewhere on the network still fails safely when no local file appears.

This avoids Paper's cache-only `getOfflinePlayerIfCached` limitation and avoids deriving offline-mode UUIDs on a Velocity backend. It also prevents a player known elsewhere on the network from being mistaken for a player with data on this server.

For playerdata created before DataRegistry was authoritative, InvTools retains a conservative local fallback. It can use an identity observed during a real connection, the player's `bukkit.lastKnownName` metadata, or a matching local entry in Paper's `usercache.json`. An existing legacy local file takes precedence over a canonical UUID whose file has not appeared, preserving access across UUID-mode migrations. A DataRegistry query failure is reported as a load failure rather than incorrectly claiming that the player never joined.

InvTools never performs a blocking Mojang profile lookup and never constructs a fake offline `Player` entity. Offline inventory access is direct, narrowly scoped playerdata I/O.

## Permissions

- `serverfeatures.feature.invtools.command.inventory.open.inspect` opens a player inventory read-only.
- `serverfeatures.feature.invtools.command.inventory.open.edit` additionally permits editing it.
- `serverfeatures.feature.invtools.command.inventory.clear` permits clearing it.
- `serverfeatures.feature.invtools.command.enderchest.open.inspect` opens an ender chest read-only.
- `serverfeatures.feature.invtools.command.enderchest.open.edit` additionally permits editing it.
- `serverfeatures.feature.invtools.command.enderchest.clear` permits clearing it.

An edit permission does not grant the matching open permission, and clear permissions are separate from both. Neither permission is granted by default.

## Session behavior

Online inventories use Paper's inventory API and remain live. The GUI refreshes from the target every `online_sync_interval_ticks` (five ticks by default), and staff edits are applied to the target immediately. Only one editor may modify an online target at a time; additional staff may still inspect that target. If the target disconnects, all live views close because the online inventory object is no longer authoritative. Before every online edit, InvTools compares the clicked slot with the target's current inventory; if it changed since the GUI was rendered, the view refreshes and requires a second click instead of applying a stale mutation.

The main storage and hotbar are deliberately distinct in the GUI and audit log. On disk they retain Minecraft's canonical slot IDs: `0–8` for the hotbar and `9–35` for main storage. Avoiding a second internal numbering scheme keeps online and offline mutations equivalent and removes a class of conversion bugs.

Offline access reads the primary world's `playerdata/<uuid>.dat` file away from the server thread. Only one offline session per target is allowed, including read-only sessions. A write:

- changes only the selected inventory or ender chest tags;
- verifies a SHA-256 revision before creating the recovery backup and again immediately before replacing the file;
- writes through a same-directory temporary file, requires an atomic move, and synchronizes it where the file system permits;
- retains the exact pre-write file as `playerdata/<uuid>.dat.invtools-backup` for recovery;
- preserves unrelated player NBT and POSIX ownership, group, and permissions.

At most `max_offline_sessions` offline sessions are active at once (four by default). New requests fail fast while that limit is reached instead of occupying Paper's shared asynchronous workers. Offline clear uses the same limit, target reservation, revision checks, atomic replacement, recovery backup, and login barrier as offline editing. An inventory with an active online editor is never cleared until that editor has closed their session.

Offline editing is isolated from the staff member's own inventory. The bottom inventory, shift transfer, number-key swaps, drags, and destructive Q shortcuts are blocked. Items may be safely rearranged inside the target GUI. Read-only offline inspection does not isolate the staff cursor or block ordinary interaction with the staff inventory. If an edit GUI closes with a target item on the cursor, that item is inserted back into the target snapshot before saving and the real cursor is cleared. This prevents duplication or loss when a write conflicts, fails, or overlaps a login.

When an offline target starts logging in, pre-login waits up to `offline_io_timeout_seconds` (ten seconds by default) for pending changes to commit before the server loads that playerdata. A failed or timed-out commit rejects that login once with a retry message, preventing stale data from overwriting staff changes. The timeout is clamped to 30 seconds to protect Paper's login threads. Configuration and join listeners guard a race that began before the offline reservation. Paper's initial-configuration event catches this before player construction where possible, with join as a final guard; both close the view and discard uncommitted GUI state instead of writing over data already being loaded. An explicit login fence rejects new offline opens from pre-login until Paper has exposed the player as online, closing the remaining transition gap.

Feature or server shutdown closes every visible and already-saving offline session. It waits for each in-flight atomic playerdata write to reach its actual terminal outcome before the lifecycle task manager is allowed to cancel remaining feature work.

Offline access is limited to playerdata from this exact Paper build. Older or future playerdata is rejected before its inventory is decoded; InvTools has one current-schema reader and writer, with no legacy compatibility paths. The exact DataVersion is obtained from the running Paper data fixer rather than maintained as a hard-coded version number.

## Operational notes

- Keep the server's normal playerdata backups; direct offline editing is deliberately conservative, but it is still an administrative data mutation.
- Failed identity resolution and temporarily missing logout files emit an InvTools warning containing the requested name, resolved UUID where available, absolute playerdata directory, exact expected `.dat` path, and directory/file type and readability state. A remaining production mismatch is therefore diagnosable without enabling a separate debug mode.
- A save conflict or malformed or oversized playerdata file is rejected and logged. It is never overwritten optimistically.
- If a current playerdata file contains a UUID identity tag, that identity must match its filename; copied or swapped files are rejected before inspection or mutation.
- Compressed and decompressed playerdata sizes are bounded before NBT parsing to reject pathological files without exhausting the server heap.
- Administrative mutations are logged by default with a session ID, actor, target, source, inventory section, canonical slot, before/after material counts, and an outcome. Online edits are recorded as applied immediately. Offline edits are recorded as pending and receive exactly one terminal saved, conflict, failed, or discarded save entry with the same session ID. Set `audit_edits: false` only if another audit system records the same actions and outcomes.
