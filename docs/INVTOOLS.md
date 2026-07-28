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
every `online_sync_interval_ticks` (two ticks by default), and staff edits are applied to the
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
- writes through a same-directory temporary file and requests an atomic move;
- preserves unrelated player NBT and file permissions.

Offline editing is isolated from the staff member's own inventory. The bottom inventory, shift
transfer, number-key swaps, and drags are blocked. Items may be rearranged inside the target GUI;
`Q` removes one item and `Ctrl+Q` removes a stack. If the GUI closes with a target item on the
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

Offline editing is limited to playerdata from this exact Paper build. Older or future playerdata is
read-only until the player has logged in on the running server version. This avoids encoding current
item components into a different file format, which cannot be made safe without a verified reverse
data-fixer.

## Operational notes

- Keep the server's normal playerdata backups; direct offline editing is deliberately conservative,
  but it is still an administrative data mutation.
- A save conflict or malformed/oversized playerdata file is rejected and logged. It is never
  overwritten optimistically.
- Administrative mutations are logged by default with actor, target, source, inventory section,
  canonical slot, and before/after material counts. Set `audit_edits: false` only if another audit
  system records the same actions.
