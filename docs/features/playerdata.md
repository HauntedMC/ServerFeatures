# PlayerData

> Paper · Feature name `PlayerData` · package `features.playerdata` · disabled by default

PlayerData is a read-only staff diagnostic feature for inspecting a player's current Paper state and persisted player `.dat` data. It is intended for debugging player-specific feature settings and server state without adding an edit surface.

## Commands and permission

Permission:

```text
serverfeatures.feature.playerdata.inspect
```

Commands:

| Command | Source | Behavior |
|---|---|---|
| `/playerdata <player>` | live/offline | Shows the default overview. |
| `/playerdata <player> overview` | live/offline | Shows identity/source metadata and persistent-data counts. |
| `/playerdata <player> runtime` | live only | Shows current world, position, game mode, health, food, experience, flight, sleep/insomnia and related runtime state. |
| `/playerdata <player> settings` | live/offline | Shows every persisted key in the `serverfeatures` PDC namespace. |
| `/playerdata <player> pdc` | live/offline | Shows all persisted Bukkit/Paper PDC keys, including other plugin namespaces. |
| `/playerdata <player> nbt` | offline only | Lists top-level tags from the player's raw `.dat` NBT. |
| `/playerdata <player> nbt <path>` | offline only | Inspects a dot-separated compound path, for example `bukkit` or `BukkitValues`. |

The command can be used by an authorized player or from console. Online-player names are suggested; an exact UUID is also accepted. Offline names are resolved from locally stored playerdata using the persisted `bukkit.lastKnownName` value, with a short-lived index so repeated staff queries do not rescan every file.

## ServerFeatures settings

`settings` is intentionally dynamic. It does not maintain a hardcoded list of feature keys. Every PDC entry whose namespace is exactly `serverfeatures` is displayed, so newly added PDC-backed settings automatically become visible.

Current examples include AutoPickup and FairPerks persistence. A setting such as PhantomToggle is shown automatically when that feature stores its key; PlayerData does not need a feature-specific dependency or update.

Byte values `0` and `1` are rendered as `0 (false)` and `1 (true)` for convenience while retaining the raw stored value. Other primitive PDC types are rendered with their type. Nested/custom values are summarized when they cannot be decoded generically.

## Live versus offline data

When the target is online, `overview`, `runtime`, `settings` and `pdc` are read from the live `Player` object. This avoids reporting a stale disk snapshot.

When the target is offline, the feature reads the local Paper player `.dat` asynchronously from the server's active playerdata directory. The read path is shared with InvTools' world-layout resolver so both features agree on Paper's current `<level>/players/data` location.

Raw `nbt` inspection is rejected while the player is online. Paper may not have flushed the latest in-memory player state to disk yet, so presenting that file as authoritative would be misleading. Staff should use the live views or inspect raw NBT after the player is offline.

## Offline read safety

Offline inspection is strictly read-only:

- no data fixer or migration is run;
- no backup is created;
- no NBT is rewritten;
- no setting can be changed or removed;
- symbolic links and non-regular playerdata files are rejected;
- compressed and decompressed size limits prevent unbounded reads;
- file scanning and NBT parsing run off the main thread;
- malformed individual records are skipped while resolving a name;
- feature shutdown cancels outstanding feature-scoped work and drops late completions.

The feature deliberately does not reuse InvTools' editable offline data store because InvTools may migrate old playerdata as part of opening it. A diagnostic inspector should never mutate a record merely because a staff member viewed it.

## NBT browser

The raw browser is intentionally conservative. A blank path lists the root compound. Dot-separated paths can descend through compounds only. Scalar values are rendered with their NBT type; compounds are summarized by child-key count; lists are identified as lists without recursively dumping potentially large content.

Examples:

```text
/playerdata Steve nbt
/playerdata Steve nbt bukkit
/playerdata Steve nbt BukkitValues
```

The browser is for diagnostics, not a replacement for `/data modify`. There are no write, delete, import, export or arbitrary-SNBT execution commands.

## Configuration

File:

```text
plugins/ServerFeatures/features/PlayerData/config.yml
```

| Key | Default | Meaning |
|---|---:|---|
| `enabled` | `false` | Loads the feature and `/playerdata`. |
| `output.max-entries` | `100` | Maximum rows rendered by a list view; clamped to `10..500`. |
| `output.max-value-length` | `240` | Maximum rendered value length; clamped to `40..2000`. |
| `offline.max-compressed-bytes` | `4194304` | Maximum compressed `.dat` file read; clamped to 256 KiB..16 MiB. |
| `offline.max-decompressed-bytes` | `33554432` | Maximum validated gzip expansion; never lower than the compressed limit and capped at 128 MiB. |

Changing these settings requires a normal feature reload because the limits are captured when the inspection service is created.

## Operational verification

After enabling PlayerData:

1. Run `/playerdata <online-player>` and verify the overview is marked `live`.
2. Run `runtime` and compare world/location/game mode with the player's current state.
3. Toggle a PDC-backed feature such as AutoPickup, run `settings`, and confirm the stored `serverfeatures:*` value updates immediately.
4. Run `pdc` and confirm non-ServerFeatures namespaces are visible without changing them.
5. Log the target out and repeat `overview` and `settings`; the source should be `offline .dat`.
6. Use `nbt`, `nbt bukkit`, and `nbt BukkitValues` on the offline player.
7. Confirm `nbt` is refused for an online target rather than presenting stale disk data.
8. Confirm an unknown player name and a UUID without a local `.dat` file return a clean not-found message.
9. Confirm the `.dat` bytes are unchanged before and after offline inspection.
