# InvTools

> Paper · Feature ID `invtools` · disabled by default · Brigadier root `/inv`

InvTools gives staff a transaction-safe interface for online and offline player inventories. It supports main inventory/hotbar/armor/offhand and 27-slot ender chest storage, separates inspect/edit/clear permissions, resolves canonical identities through DataRegistry, and performs offline playerdata reads, migrations and writes under bounded per-target locks and login barriers.

Item-NBT-API is shaded into ServerFeatures. It is not a separate server plugin dependency.

## Command tree

```text
/inv inventory open <player>
/inv inventory clear <player>
/inv enderchest open <player>
/inv enderchest clear <player>
```

Every command is player-only. Console has no branch because Brigadier `.requires(...)` checks that the sender is a Player.

Player suggestions contain online names only, filtered by prefix, sorted case-insensitively and limited to 20. Offline names must be typed manually.

Before an offline open/clear, the command registers a migration request with the actor and typed target name. Exact online targets skip that request and use live Bukkit state.

## Permissions

| Permission | Operation |
|---|---|
| `serverfeatures.feature.invtools.command.inventory.open.inspect` | Open player inventory read-only. |
| `serverfeatures.feature.invtools.command.inventory.open.edit` | Edit an opened player inventory, but does not grant inspect/open by itself. |
| `serverfeatures.feature.invtools.command.inventory.clear` | Clear inventory, hotbar, armor and offhand. |
| `serverfeatures.feature.invtools.command.enderchest.open.inspect` | Open ender chest read-only. |
| `serverfeatures.feature.invtools.command.enderchest.open.edit` | Edit an opened ender chest, but does not grant inspect/open by itself. |
| `serverfeatures.feature.invtools.command.enderchest.clear` | Clear all 27 ender chest slots. |

Open, edit and clear are deliberately independent. Permission is checked when the command is built/executed and again while a session remains open. Losing inspect/edit permission closes or downgrades the session rather than allowing stale authority.

Self-open is rejected.

## Configuration

| Key | Default | Runtime behavior |
|---|---:|---|
| `enabled` | `false` | Enables the administrative feature. |
| `online_sync_interval_ticks` | `5` | Refresh interval for live target views. Clamped to at least 1 tick. |
| `offline_io_timeout_seconds` | `10` | Maximum async pre-login wait for admitted offline operations. Service clamps to 1–30 seconds. |
| `max_offline_sessions` | `4` | Concurrent offline opens and clears. Excess work fails fast rather than entering Paper's shared async queue. |
| `audit_edits` | `true` | Emits structured edit/clear and offline terminal-outcome logs. |

There are no configurable GUI sizes, backup names, file-size limits, retry count, migration policy, slot numbering or database connection. Those are implementation contracts.

## Initialization order

Feature initialization is intentionally fail-fast:

1. call `NBT.preloadApi()`; incompatible bundled Item-NBT-API aborts initialization;
2. construct `PlayerDataMigrationCoordinator`, which validates Paper/Item-NBT DataVersion/data-fixer contracts;
3. create `InvToolsService` through the factory;
4. register Brigadier command;
5. register listeners in this order:
   - transfer-abort listener;
   - offline cursor interaction listener;
   - deterministic shift-transfer/settlement listener;
   - main InvTools/login listener;
6. schedule live-view refresh after one interval and repeat at the configured interval.

If service creation fails, the migration coordinator is shut down before the exception escapes.

## Event ordering

### Main listener

| Event | Priority | Behavior |
|---|---|---|
| `InventoryClickEvent` | `HIGHEST` | Main session permission, slot, online-revision and GUI handling. Processes cancelled events. |
| `InventoryDragEvent` | `HIGHEST` | Cancels target-GUI/isolated-cursor drags. Processes cancelled events. |
| `InventoryCloseEvent` | `MONITOR` | Starts close/save/discard lifecycle. |
| `AsyncPlayerPreLoginEvent` | `HIGHEST` | Rejects active migration or waits for admitted offline I/O. Only acts when login is still allowed. |
| `PlayerConnectionInitialConfigureEvent` | `LOWEST` | Rechecks migration fence before Paper constructs/loads player state and marks playerdata loading. |
| `PlayerJoinEvent` | `LOWEST` | Completes login transition and closes/discards conflicting offline state. |
| `PlayerQuitEvent` | `MONITOR` | Closes target live views and viewer sessions. |

### Offline cursor listener

`InventoryClickEvent` and `InventoryDragEvent` run at `HIGH`, `ignoreCancelled=true`. They implement an isolated virtual target cursor and journal changes to both target snapshot and staff storage. The later HIGHEST main listener remains the final GUI safety layer.

### Transfer listener

- shift-click and settlement-protection events run at `HIGH`, `ignoreCancelled=true`;
- `InventoryCloseEvent` and `PlayerQuitEvent` run at `LOWEST`;
- while an offline cross-inventory transaction is settling, drag, drop, swap-hand, consume, interact and item-pickup are cancelled;
- the view can be reopened each tick until terminal settlement so the staff member cannot mutate transfer-sensitive items elsewhere.

### Disconnect/death abort listener

`InventoryCloseEvent` runs at `LOWEST` and is registered before the normal transfer listener. On close reason `DISCONNECT` or `DEATH`, offline cursor/transfers are rolled back before Paper persists the staff member.

## Online identity and storage

Online targets use Bukkit/Paper inventory objects and are authoritative immediately.

The GUI refreshes from the target every `online_sync_interval_ticks`. Before each edit, InvTools compares the displayed slot with a fresh live snapshot. If the target changed that slot since render, the GUI refreshes and the staff member must repeat the action.

Only one editor may mutate an online target at a time. Additional staff can inspect. A clear is rejected while an editor owns the target.

When the target quits, all live views close because the Bukkit inventory object is no longer authoritative; InvTools does not silently convert an open online session into offline file editing.

## Canonical slots and GUI layout

The GUI visually separates sections but preserves Minecraft's canonical backing slots:

- hotbar: `0–8`;
- main storage: `9–35`;
- armor/offhand: their canonical playerdata equipment slots/constants;
- ender chest: `0–26`.

Decorative GUI positions never become backing slots. Shift transfers cancel Bukkit's native bulk routing and use feature-owned routing:

1. merge compatible stacks;
2. use normal/main storage before hotbar when inserting into player inventory;
3. do not unexpectedly auto-equip armor/offhand;
4. enforce armor item compatibility for direct offline cursor placement.

Online target and staff changes are applied as one guarded operation; an exception restores both snapshots where possible.

## Offline identity resolution

Identity and local data ownership are separate checks.

1. DataRegistry resolves the requested name to the network's canonical forwarded UUID, using active cache then persisted identities.
2. InvTools requires a regular, non-symbolic local file at:

```text
<Paper level root>/players/data/<uuid>.dat
```

Paper 26.1's `players/data` layout is authoritative. The removed `<level>/playerdata` path is not used. The level directory comes from Paper, not a hard-coded world name.

A player can be known network-wide but have no file on this backend; that request fails safely.

### Logout appearance race

Paper can fire quit before its final save is visible. A UUID confirmed through a real connection/DataRegistry is allowed into a bounded file-appearance retry. Identity resolution must not reject the request before this retry becomes reachable.

### Conservative legacy fallback

For playerdata predating canonical DataRegistry authority, InvTools can use:

- UUID observed from a real local connection;
- `bukkit.lastKnownName` inside playerdata;
- matching local Paper `usercache.json` entry.

An existing local legacy UUID file can take precedence over a canonical UUID whose file has not appeared, preserving access during UUID-mode migrations. DataRegistry query failure is reported as load failure, not “never played.”

No blocking Mojang profile lookup or fake OfflinePlayer entity is used.

## Offline session admission

Offline reads/writes run away from the main thread. Admission is bounded by:

- `max_offline_sessions` global permit count;
- one exclusive offline session/reservation per target, including read-only sessions;
- login fence preventing new offline opens after pre-login begins;
- per-target operation lock;
- operation gate that rejects not-yet-started work during shutdown while allowing admitted work to reach a terminal state.

Offline clear uses the same admission, lock, conflict, backup, login and migration safeguards as editing.

## Offline cursor and cross-inventory transfers

The target cursor is detached from Bukkit's real cursor. This prevents ordinary bottom-inventory behavior, number keys, Q/drop and mixed drags from mutating target data outside the transaction.

Direct cursor editing is supported on target and staff storage with these restrictions:

- cross-side stack merging requires first settling the cursor;
- drag must remain within one side;
- shift-click is the supported cross-inventory movement;
- armor slots accept only matching equipment;
- closing with target-owned cursor content inserts it safely back into the target snapshot before save.

Each staff-side transfer is journaled with inverse operations. During close settlement:

- successful target save commits staff changes;
- conflict/failure/discard replays inverse staff operations;
- rollback merges returned items;
- leftover rollback items are dropped beside the staff member rather than deleted;
- disconnect/death performs early abort before staff playerdata save.

This prevents one side of an offline transfer committing without the other.

## Offline file transaction

A write changes only the selected inventory or ender chest tags and preserves unrelated NBT.

Transaction outline:

1. bounded compressed/decompressed read;
2. parse and validate UUID identity where present;
3. calculate source SHA-256 revision;
4. edit detached NBT snapshot;
5. recheck revision before backup;
6. create exact pre-write recovery file:
   `<uuid>.dat.invtools-backup`;
7. recheck source immediately before replacement;
8. write same-directory temporary file;
9. fsync where supported;
10. require atomic move to replace source;
11. reread/validate committed data;
12. preserve POSIX owner/group/permissions when supported.

A revision mismatch produces conflict and never overwrites the newer file. Malformed, oversized, symbolic or wrong-UUID files are rejected.

The ordinary `.invtools-backup` is intentionally retained for administrative recovery after a successful edit.

## Login barriers

`AsyncPlayerPreLoginEvent` may wait up to the clamped timeout for a pending admitted save/clear to finish before Paper reads playerdata.

- success permits login;
- failed/timed-out completion rejects once with `invtools.login_retry`;
- active migration rejects with `invtools.login_migration`;
- initial-configuration event checks again before player construction;
- join is the final race guard and discards uncommitted GUI state rather than writing over already-loaded playerdata.

The migration fence covers queue wait, conversion, recovery and final save. The ordinary reservation/login fences protect the earlier identity handoff.

## Playerdata version and migration

InvTools uses one current-schema reader/writer. Older positive `DataVersion` values are upgraded using Paper's own `DataFixTypes.PLAYER` public `CompoundTag` conversion path.

Initialization requires:

- Item-NBT-API target DataVersion exactly matching Paper runtime DataVersion;
- resolvable compatible Paper player data fixer overload.

It fails feature initialization when these contracts are unavailable.

Rejected without mutation:

- missing DataVersion;
- non-integer/non-positive DataVersion;
- malformed NBT;
- data newer than the running Paper build;
- UUID mismatch;
- invalid converted inventories/equipment/items/slot uniqueness/stack sizes.

### Migration transaction

1. read, bound, parse, identity-check and hash source;
2. copy exact compressed bytes to same-directory temporary migration backup;
3. fsync, atomically install and hash-verify `<uuid>.dat.invtools-migration-backup`;
4. convert detached NBT with Paper;
5. validate converted root;
6. write/fsync/reread/validate converted temporary file;
7. recheck source hash;
8. atomically install converted source;
9. reread and compare committed bytes;
10. delete temporary migration backup only after verified success.

A failure before replacement leaves source unchanged. A failure after replacement restores exact backup through another verified atomic replacement. When another process changed a parseable file, InvTools does not guess or overwrite it.

### Leftover migration-backup reconciliation

On the next access:

- valid current target + verified old backup: treat prior commit as successful and remove stale backup;
- missing/unreadable target + verified backup: restore backup;
- two different parseable files: retain both as ambiguous and fail closed;
- backup-only state remains discoverable even when primary source is absent.

Migration backup is temporary and distinct from the retained edit recovery backup.

## Audit logging

With `audit_edits=true`, logs include:

- actor name/UUID;
- target name/UUID;
- session ID;
- source `online`/`offline`;
- outcome (`applied`, `pending`, and one terminal offline outcome);
- inventory kind;
- visual section;
- canonical backing slot;
- before/after material and amount.

Offline pending edits receive exactly one terminal save/conflict/failure/discard outcome with the same session ID. Clear operations and migration progress/failures are also logged.

Disable auditing only when another system records equivalent action **and terminal persistence outcome**.

## Messages and variables

The feature defines complete user feedback groups:

### Identity/admission

- `invalid_name`, `not_played_here`, `loading`, `busy`, `load_failed`, `self`, `already_open`, `already_editing`;
- `{player}` is supplied where present.

### Session/permissions/interactions

- `opened_inspect`, `opened_edit`, `read_only`, `permission_revoked`, `edit_permission_revoked`;
- cursor/drag/interaction errors;
- online target offline/logging-in and join-conflict messages.

### Save/clear

- `offline_saved`, `save_failed`, `save_conflict`;
- `clearing`, `cleared`, `clear_failed`, `clear_conflict`, `clear_cancelled`, `clear_editing`.

### Login/migration

- retry/migration login rejection;
- detected/backup/converting/restoring/completed/backup-retained/failure variants;
- migration messages supply `{player}`, `{from}` and `{to}` as applicable.

### GUI

Localized inventory/ender chest titles, online/offline status, section descriptions, edit/inspect mode and close item.

## Shutdown sequence

`disable()` first calls `service.shutdown()` and then always shuts down the migration coordinator.

Service shutdown closes visible sessions, settles/rolls back transfer state, closes the operation gate, rejects queued work that never started, and allows operations already admitted through the gate to finish before login protection is removed.

This ordering prevents a queued legacy load from starting after listeners/fences disappear.

## Persistence and non-integrations

InvTools uses:

- DataRegistry for canonical identity/name history;
- local Paper playerdata files for inventory persistence;
- local logs for audit.

It does not use:

- MySQL for inventory contents;
- Redis/network locking;
- ProxyFeatures commands/messaging;
- PlaceholderAPI;
- Bukkit OfflinePlayer inventory loading;
- external Mojang profile lookup.

Only one backend can safely mutate a given local playerdata file. Shared/mounted playerdata across independently running backends requires a stronger distributed ownership design than this local lock.

## Important operational boundaries

- Direct offline editing is an administrative data mutation; maintain normal backups.
- Online and offline paths have different authority and concurrency models.
- A player known elsewhere on the network may have no local data.
- Offline save and reward-like staff transfer settlement are transactional only within this process/filesystem design.
- Atomic move support is required for mutation/migration.
- Login can be temporarily rejected to preserve playerdata ordering.
- Newer playerdata cannot be down-converted.
- Recovery backups may intentionally remain after ambiguous/failing recovery.
- File-size, UUID and item validation fail closed.
- Permission loss and target login/quit close sessions rather than continuing with stale authority.

## Verification checklist

1. Test all six permissions independently; confirm edit does not imply inspect and clear is separate.
2. Open/edit/clear online inventory and ender chest while target concurrently moves items.
3. Open multiple inspectors and a single editor; attempt clear during edit.
4. Exercise every click, shift-click, number key, offhand, drag, drop and cursor-close path.
5. Fill staff inventory before offline rollback and confirm leftovers drop rather than disappear.
6. Test canonical UUID, renamed player, legacy UUID, unknown network player and no-local-file cases.
7. Trigger logout file-appearance retry and login during open/save/clear/migration.
8. Modify playerdata externally between read, backup and commit to validate both revision fences.
9. Test malformed, oversized, symlink, wrong-UUID and newer-DataVersion files.
10. Migrate representative old versions and interrupt before/after replacement to verify restoration/reconciliation.
11. Disable/reload/server-stop with queued and admitted operations.
12. Inspect structured audit entries and exact terminal outcome correlation by session ID.

## Source map

- Defaults/messages/init/shutdown: `features/invtools/InvTools.java`
- Brigadier tree: `features/invtools/command/InvToolsCommand.java`
- Main GUI/login lifecycle: `features/invtools/listener/InvToolsListener.java`
- Offline cursor: `features/invtools/listener/InvToolsOfflineInteractionListener.java`
- Shift transfer/settlement: `features/invtools/listener/InvToolsTransferListener.java`
- Disconnect/death rollback: `features/invtools/listener/InvToolsTransferAbortListener.java`
- Session/orchestration: `features/invtools/service/InvToolsService.java`
- Playerdata persistence: `features/invtools/persistence/`
- Migration/fences: `features/invtools/migration/`
- GUI/snapshots/slot mapping: `features/invtools/gui/`, `features/invtools/model/`
