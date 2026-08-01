# Holograms

> Paper · Feature name `Holograms` · feature package `features.holograms` · disabled by default

Holograms loads static `TextDisplay` definitions from `local/holograms.yml`, resolves their text once from sequential localization keys, and spawns one non-persistent display entity per definition whose world is currently loaded. Text, appearance and transformation are static until feature reconstruction; there is no viewer filtering, command, permission, scheduled placeholder refresh, database or Redis integration.

## Commands and permissions

No command or permission is registered. Every client that can track the TextDisplay according to Minecraft/Paper view rules can see it.

There is no per-player visibility condition, world permission, vanish filter, locale-specific entity, edit/reload command or administrative API.

## Feature configuration

File: `plugins/ServerFeatures/features/Holograms/config.yml`.

| Key | Default | Meaning |
|---|---:|---|
| `enabled` | `false` | Loads definitions/text, registers world listeners and schedules initial spawning. |

All definitions live in `local/holograms.yml`; text lives in localization message files.

## `local/holograms.yml` complete reference

Path:

```text
plugins/ServerFeatures/local/holograms.yml
```

Expected root:

```yaml
holograms:
  spawn:
    world: world
    x: 0.5
    y: 65.0
    z: 0.5
    yaw: 0.0
    pitch: 0.0
    billboard: CENTER
    alignment: CENTER
    line_width: 0
    see_through: false
    shadowed: true
    use_default_background: true
    background_color: '#80000000'
    glow: false
    glow_color: '#FFFFFFFF'
    view_range: 1.0
    brightness:
      block: 15
      sky: 15
```

Each direct child under `holograms` becomes one definition. Child order is retained by `LinkedHashMap` and controls spawn iteration/logical ordering, but each definition remains one independent entity.

### Definition fields

| Field | Default | Exact behaviour |
|---|---:|---|
| child ID | required map key | Used as definition ID, text-key segment and spawned-map key. Registry lookup is lowercase, but `spawned` uses original ID. IDs differing only by case overwrite the registry entry. |
| `world` | `world` | Exact Bukkit world name for `Server#getWorld`. Resolution is case-sensitive according to Bukkit lookup; world-load matching is case-insensitive. Missing world is silently skipped by handler. |
| `x` | `0.0` | Spawn X double. |
| `y` | `64.0` | Spawn Y double. |
| `z` | `0.0` | Spawn Z double. |
| `yaw` | `0.0` | Location yaw float. Billboard mode can make apparent rotation independent of this. |
| `pitch` | `0.0` | Location pitch float. |
| `billboard` | `CENTER` | `Display.Billboard` enum: `FIXED`, `VERTICAL`, `HORIZONTAL`, `CENTER`. Enum parsing follows config service conversion. |
| `alignment` | `CENTER` | `TextDisplay.TextAlignment`: `LEFT`, `CENTER`, `RIGHT`. |
| `line_width` | `0` | Clamped to at least zero in model. Handler calls `setLineWidth` only when >0; zero leaves Paper's entity default. |
| `see_through` | `false` | `TextDisplay#setSeeThrough`. |
| `shadowed` | `true` | `TextDisplay#setShadowed`. |
| `use_default_background` | `true` | When true, sets default background and ignores custom background colour. |
| `background_color` | null | `#RRGGBB` or `#AARRGGBB` string. Six digits receive alpha `FF`. Invalid values become null silently. Applied only when default background is false. |
| `glow` | `false` | Sets entity glowing flag. |
| `glow_color` | null | Same ARGB parser. Applied as glow-colour override only when `glow=true`; errors are swallowed. |
| `view_range` | null | Optional float passed to `TextDisplay#setViewRange`; errors are swallowed. Paper display view range is a multiplier/engine-specific value, not a direct block count documented by this feature. |
| `brightness.block` | null | Optional light level clamped to `0..15`. |
| `brightness.sky` | null | Optional light level clamped to `0..15`. |

When either brightness component is present, both are supplied to `Display.Brightness`; the missing component defaults to zero. Configuring only block brightness therefore forces sky to 0, and vice versa.

There are no fields for scale, translation, interpolation, opacity, text opacity, background alpha separately, teleport duration, shadow radius/strength, display height/width, tags, persistence, click actions or refresh interval.

## Hard-coded entity transformation

Every spawned display attempts to receive:

```text
translation: (0,0,0)
left rotation: identity
scale: (3.5,3.5,3.5)
right rotation: identity
```

Transformation errors are swallowed. Scale is not configurable.

The entity is marked `persistent=false`, so Paper does not save it to world storage. Feature startup/world load must recreate it.

## Text/localization contract

Text is **not** stored in `holograms.yml`. For definition ID `<id>`, registry scans:

```text
holograms.hologram.<id>.0
holograms.hologram.<id>.1
...
```

up to index 255.

The feature's default messages provide example `spawn.0` through `spawn.4`, with `<end>` on line 4.

### Line resolution algorithm

For every definition during registry reload:

1. build each message with `LocalizationHandler#getMessage(key).build()` without player audience;
2. remove literal component text `<end>` everywhere using `replaceText(matchLiteral)`;
3. determine termination by comparing component before/after removal;
4. force italic decoration false on the root line component;
5. append line, including empty lines;
6. stop after a line containing `<end>` or after 256 iterations.

Despite source comment saying scan until a missing key, the implementation does not explicitly test key existence. It always calls `getMessage` and appends the resulting component. Behaviour for missing keys depends on localization-handler fallback. Without an `<end>` marker, a definition can cache 256 fallback/missing-key lines.

`lines.isEmpty()` fallback to the ID is practically unreachable because loop executes at least index 0 unless an exception aborts.

The cached lines are joined with newline components into one `TextDisplay` text component. There is no one-entity-per-line spacing model.

### Audience/placeholders

Messages are built once with no player audience. Therefore:

- player-specific localization is not possible;
- player-context PlaceholderAPI values are not intentionally available;
- changing localization/PAPI values does not refresh the entity;
- all viewers see the same component;
- global/static formatting supported by localization is resolved at load time.

The feature does not call `PlaceholderAPIHook` or register a PAPI expansion.

## Registry reload semantics

`HologramRegistry` constructor opens config view and immediately calls `reload`:

- clears definitions and cached lines;
- reads current config nodes;
- rebuilds definitions;
- resolves/caches all text;
- logs counts.

There is no command/file watcher and `reload()` is not exposed through a feature command. A standard feature reload/re-enable reconstructs registry/handler and applies changes.

Calling registry reload programmatically does not automatically respawn entities; caller must invoke `spawnAllSafe` afterward.

## Spawn and ownership model

`HologramHandler` tracks:

```text
Map<String,List<UUID>> spawned
```

Each normal definition currently produces one UUID, but list structure allows multiple.

`spawnAllSafe`:

1. removes every tracked entity;
2. iterates all definitions;
3. resolves world;
4. silently skips missing worlds;
5. spawns a TextDisplay for loaded worlds.

There is no log per missing world despite method comment saying it logs them.

### Applied TextDisplay properties

- joined cached text;
- billboard;
- alignment;
- line width when positive;
- see-through;
- shadowed;
- default/custom background;
- glowing and optional override;
- optional view range;
- optional brightness;
- hard-coded 3.5 scale;
- non-persistent.

Optional/newer API calls for glow override, view range, brightness and transformation are wrapped in broad `Throwable` catches and fail silently. Operators may see partial appearance without logs on incompatible Paper versions.

The display has no scoreboard tag, custom name/PDC marker, invulnerability configuration, interaction prevention or ownership metadata outside the in-memory UUID map.

## World lifecycle events

Listeners use default `NORMAL` priority.

### `WorldLoadEvent`

When any definition's configured world name equals the loaded world case-insensitively, schedules a one-time `spawnAllSafe` and breaks.

`spawnAllSafe` removes and respawns **all holograms in all loaded worlds**, not only those for the newly loaded world. Loading one relevant world causes visible entity UUID replacement/flicker for every existing hologram.

Multiple relevant world-load events can schedule repeated global respawns.

### `WorldUnloadEvent`

Unconditionally calls `removeAll()` for **every** world unload, even when no hologram is configured in that world. Unloading an unrelated world removes holograms from all loaded worlds. They are not restored until a relevant world later loads or the feature is re-enabled.

This is a major operational behaviour; dynamic world management can make all holograms disappear.

### Initial spawn

Feature initialization schedules one one-time `spawnAllSafe` so worlds are more likely ready. No explicit tick delay is supplied; timing follows lifecycle task-manager semantics.

## Removal semantics

`removeAll` iterates tracked UUIDs, resolves each through `Server#getEntity`, removes live `TextDisplay` instances, then clears map.

- Missing/unloaded/dead entities are ignored.
- An entity whose UUID is no longer tracked cannot be cleaned.
- Since entities are non-persistent, untracked leftovers normally disappear on chunk/world lifecycle, but they can remain until unload if map state is lost without removal.

`remove(hologramId)` performs exact map-key lookup, not lowercase normalization. Calling with different case than original definition ID fails to find it, even though registry lookup is case-insensitive.

No public command/API invokes individual removal.

## Interaction and damage

TextDisplay is not an `Interaction` entity and normally has display-specific hit behaviour. The feature registers no damage/interact listener and does not tag/secure entities. Administrative/entity-clearing plugins can remove them; no health/reconciliation task restores them.

A removed entity remains absent until global respawn.

## Performance

Normal runtime has no repeating tasks. Cost occurs during:

- registry text scan: up to 256 message builds per definition;
- global spawn/removal on enable or relevant world load;
- unconditional global removal on any world unload.

Large definitions without `<end>` multiply localization components and TextDisplay text size. Always terminate lines explicitly.

## Persistence, database and messaging

Holograms has no DataProvider/database, Redis, proxy messaging, API registration or PAPI expansion. Definitions/localization are disk configuration; entities are deliberately non-persistent runtime objects.

## Lifecycle

Initialization:

1. construct/reload registry;
2. construct handler;
3. register world listener;
4. schedule initial global spawn.

Disable calls `handler.removeAll`; lifecycle cleanup cancels pending spawn tasks/unregisters listener. Non-persistent entities should not survive graceful disable when tracked.

## Developer source map

- Defaults/lifecycle: `features/holograms/Holograms.java`
- Definition/config/text cache: `features/holograms/registry/HologramRegistry.java`
- Definition validation/ARGB: `features/holograms/model/HologramDefinition.java`
- Spawn/property/removal: `features/holograms/internal/HologramHandler.java`
- World events: `features/holograms/listener/HologramListener.java`
- Definition tests: `src/test/.../features/holograms/model/HologramDefinitionTest.java`

## Operational verification

1. Test every billboard/alignment and valid/invalid ARGB form.
2. Test default/custom backgrounds, glow overrides, view range and partial brightness.
3. Verify hard-coded 3.5 scale and line-width zero/default semantics.
4. Create empty lines and explicit `<end>`; remove marker and observe 256-line fallback behaviour.
5. Test localization changes and confirm no runtime/player-specific refresh.
6. Configure missing and late-loading worlds.
7. Load a relevant world and observe all-world respawn/UUID replacement.
8. Unload unrelated world and verify current global-removal behaviour.
9. Remove a display externally and confirm no automatic repair.
10. Reload/disable and verify tracked non-persistent entities are removed.
11. Test target Paper versions where optional display setters throw; inspect silent degradation.
12. Stress-test many definitions/long lines.

## Troubleshooting

- **Hundreds of unexpected lines:** add `<end>`; missing-key detection is not explicit and scan caps at 256.
- **Player placeholders/languages do not vary:** text is cached once without audience.
- **All holograms disappear when another world unloads:** unload listener removes all definitions unconditionally.
- **Other holograms flicker when a world loads:** relevant world load triggers global remove-and-respawn.
- **Missing-world hologram produces no warning:** handler silently skips it.
- **Appearance option has no effect and no error:** several setters swallow all throwables for compatibility.
- **Individual remove by different-case ID fails:** spawned map uses original case/exact key.
- **Scale cannot be changed:** 3.5 is hard-coded.
