# Balloons

> Paper · Feature name `Balloons` · feature package `features.balloons` · disabled by default

Balloons provides local cosmetic companions rendered with two server entities: an invisible leashed parrot acts as the moving anchor and an invisible gravity-free armor stand carries the configured item/head as the visible balloon. Players select cosmetics through a paged GUI; all state is in memory and is removed on quit, death, mounting, feature shutdown, or explicit removal.

## Behaviour at a glance

- Balloon definitions are loaded from `local/balloons.yml`, not from the feature config.
- Every definition has its own permission; the feature also requires a shared use permission.
- A balloon is one hidden parrot plus one hidden armor stand.
- A repeating task runs every two ticks to keep the anchor near the player and teleport the visual to the anchor.
- Teleports remove and recreate both entities after a ten-tick delay.
- Balloons are removed on quit, death, and when the player mounts another entity.
- There is no persistence, database integration, Redis synchronization, API registration, or PlaceholderAPI expansion.

## Commands and permissions

The registered command root is `/balloons` with no aliases.

| Syntax | Permission | Sender | Behaviour |
|---|---|---|---|
| `/balloons` | `serverfeatures.feature.balloons.use` | Player only | Opens the paged balloon selection GUI. |
| `/balloons remove` | No explicit command-level permission check | Player only | Removes the active balloon, if any. The command still rejects players currently inside a vehicle before processing `remove`. |

### Permission details

- Shared use permission: `serverfeatures.feature.balloons.use`.
- Per-definition permission: configured under `balloons.<id>.permission`; default is `serverfeatures.feature.balloons.<lowercase-id>`.
- `setBalloon()` rechecks both permissions even after the GUI permission layer, so direct/internal callers cannot bypass the runtime checks.
- A locked definition is shown as a barrier in the GUI rather than hidden, provided the viewer has the shared use permission.
- The `remove` argument is suggested to every command sender; tab completion does not check player status or permission.

Console execution has an unusual side effect: it calls `feature.getConfigHandler().reloadConfig()` and then sends `general.player_command`. This reloads the feature config only; it does **not** call `BalloonRegistry.reloadFromConfig()` and therefore does not refresh `local/balloons.yml` definitions.

Players inside a vehicle receive `balloons.cannot_open_vehicle` for both menu opening and `/balloons remove`. Mounting after a balloon is active removes the balloon automatically.

## Feature configuration

File: `plugins/ServerFeatures/features/Balloons/config.yml`.

| Key | Default | Meaning |
|---|---:|---|
| `enabled` | `false` | Enables registry loading, entity management, listeners, GUI command, and the two-tick tether task. |

There are no configurable offsets, movement thresholds, task intervals, entity types, particles, menu slots, or persistence settings in the current feature config. Those values are hard-coded in the implementation.

## Balloon definition configuration

Definitions are loaded from:

```text
plugins/ServerFeatures/local/balloons.yml
```

Expected shape:

```yaml
balloons:
  example:
    permission: serverfeatures.feature.balloons.example
    displayname: '<yellow>Example Balloon'
    item: PAPER
    custom_model_data: 1001
    # head: '<base64 texture>'
```

Each direct child of `balloons` becomes one definition. Child order is retained in a `LinkedHashMap` and therefore determines menu ordering.

### Per-definition fields

| Field | Default/fallback | Behaviour |
|---|---|---|
| `<id>` | required map key | Normalized to lowercase with `Locale.ROOT` and used for registry lookup/default permission. Duplicate IDs that differ only by case overwrite the earlier entry while the loaded counter still increments. |
| `permission` | `serverfeatures.feature.balloons.<lowercase-id>` | Permission required to select/activate this definition. |
| `displayname` | falls back to `display_name`, then raw ID | Parsed as mixed text input into an Adventure component with all default formatting features. |
| `display_name` | fallback alias | Used only when `displayname` is absent/null. |
| `item` | none | Bukkit material name. When valid, item mode takes precedence over `head`. Invalid values are warned and treated as no item. |
| `custom_model_data` | none | Applied only to a valid item definition and only when the integer is greater than zero. Uses Paper's component-based custom-model-data API and stores one float value. |
| `head` | none | Base64 texture property used when no valid `item` exists. |

### Definition precedence and invalid states

`BalloonDefinition.asHelmetItem()` behaves as follows:

1. if `itemMaterial != null`, create that material and optionally apply custom-model data;
2. otherwise create a `PLAYER_HEAD`, create a random-UUID Paper profile, and set a `textures` profile property from `head`.

Although `isHead()` checks for non-blank texture data, `asHelmetItem()` does not require `isHead()` before constructing the fallback head. A definition with neither a valid `item` nor a non-blank `head` therefore still creates a player head with the configured texture value, which may be null/blank and should be treated as invalid configuration.

A definition may specify both `item` and `head`; item mode wins.

### Reload semantics

The registry loads once during feature initialization. There is no player/admin reload command and no file watcher. To apply changes to `local/balloons.yml`, use the supported feature reload/re-enable path that reconstructs the registry. Calling the normal feature-config reload alone is insufficient.

## Menu layout and interaction

The menu is a six-row (`54` slot) `PagedMenu`.

### Fixed layout

- Content: rows 1–3, columns 1–7, totaling 21 definitions per page.
- Previous page: slot `45`.
- Remove: slot `46`.
- Status: slot `49`.
- Close: slot `52`.
- Next page: slot `53`.
- Page number is shown in the title; there is no separate page-info item.
- Filler items come from `GuiItemHelper.filler()`.

The menu title and navigation labels come from localization. Entries are generated from the registry snapshot obtained when `open()` is called.

### Definition entries

For an allowed balloon:

- the menu icon is a clone of the same item/head used on the armor stand;
- display name uses `balloons.menu.balloon.name` with `{name}`;
- lore uses `balloons.menu.balloon.lore.allowed`;
- clicking calls `setBalloon`, sends `balloons.set` with `{name}` on success, applies a 120 ms click cooldown, and closes the menu.

For a locked balloon:

- the icon is replaced with `BARRIER`;
- display name remains visible;
- lore uses `balloons.menu.balloon.lore.locked`;
- the item framework's permission gate prevents the activation action.

### Control items

- **Remove:** `MILK_BUCKET`, 150 ms cooldown, closes the menu and sends either `balloons.removed` or `balloons.no_active`.
- **Status:** `CLOCK`, displays `balloons.menu.status.active` with `{name}` or `balloons.menu.status.inactive`; its lore is `balloons.menu.status.lore`.
- **Close:** `BARRIER`, 150 ms cooldown, closes the inventory.

The status component is calculated when the menu is built. It is not dynamically refreshed after selection because selection closes the menu.

## Messages and variables

| Key | Variables | Purpose |
|---|---|---|
| `balloons.menu.title` | none | GUI title. |
| `balloons.menu.balloon.name` | `{name}` | Definition entry title. |
| `balloons.menu.balloon.lore.allowed` | none | Available entry lore. |
| `balloons.menu.balloon.lore.locked` | none | Locked entry lore and the reason component passed to `general.no_permission_reason`. |
| `balloons.menu.remove.name` | none | Remove control title. |
| `balloons.menu.remove.lore` | none | Remove control lore. |
| `balloons.menu.close.name` | none | Close control title. |
| `balloons.menu.close.lore` | none | Close control lore. |
| `balloons.menu.status.active` | `{name}` | Active status title. |
| `balloons.menu.status.inactive` | none | Inactive status title. |
| `balloons.menu.status.lore` | none | Status lore. |
| `balloons.menu.prev` | none | Previous-page label. |
| `balloons.menu.next` | none | Next-page label. |
| `balloons.removed` | none | Successful removal. |
| `balloons.no_active` | none | Removal requested without an anchor entry. |
| `balloons.set` | `{name}` | Successful activation/switch. |
| `balloons.cannot_open_vehicle` | none | Command rejected while mounted/in a vehicle. |

## Runtime entity model

Three concurrent maps are keyed by player UUID:

- `active`: selected `BalloonDefinition`;
- `parrots`: hidden anchor entity;
- `stands`: hidden visual entity.

The maps are concurrent, but all normal Bukkit entity work is expected on the main server thread.

### Anchor parrot

Spawned two blocks above the player's current location with:

- entity type `PARROT`;
- invisible;
- silent;
- invulnerable;
- scoreboard tag `ServerFeaturesBalloons`;
- leash holder set to the player.

The parrot remains a real server entity and participates in entity tracking/chunk lifecycle despite being invisible.

### Visual armor stand

Spawned at the same initial location with:

- entity type `ARMOR_STAND`;
- invisible body;
- gravity disabled;
- invulnerable;
- item pickup disabled;
- arms disabled;
- base plate disabled;
- scoreboard tag `ServerFeaturesBalloons`;
- configured balloon item in the helmet slot;
- add/change equipment locks on head, chest, legs, feet, hand, and off-hand.

The stand itself is teleported to the parrot every tether tick at Y offset `-1.3`.

### Switching balloon

When the player's UUID already has a parrot entry:

- a live armor stand receives the new helmet in place;
- if the stand is absent/dead, `removeBalloon()` is called and both entities are respawned;
- `active` is updated after entity work succeeds.

When no parrot entry exists, both entities are spawned.

No check prevents choosing the already-active definition; it simply re-applies/switches the helmet.

## Tether task and movement rules

The handler schedules a repeating lifecycle task every two ticks. There is no separate initial delay argument in the feature call.

For a snapshot of current parrot UUID keys:

1. read parrot and armor stand; if either is null, skip without repairing/removing the other;
2. resolve the local online player;
3. remove internal state/entities when the player is absent/offline;
4. if the parrot world differs from the player world, teleport the parrot to the player;
5. calculate parrot-to-player distance;
6. if distance is greater than `10.0`, teleport the parrot to the player;
7. otherwise, when distance is below `6.0` and the parrot is still leashed, call `Distance.line`;
8. teleport the armor stand to the parrot location plus Y `-1.3`.

### `Distance.line` physics

Only runs in the same world.

- When distance is greater than `3.8`, add velocity toward the player with magnitude `0.2`.
- When distance is below `3.0`, add upward velocity `(0, 0.3, 0)`.
- It calls `parrot.getLocation().setDirection(...)`, but because the mutated `Location` is not teleported back to the entity, this direction assignment does not directly update entity rotation.

The condition in `tetherTick` calls this helper only while distance is **below** `6.0`. Distances from `6.0` through `10.0` receive neither follow velocity nor teleport until they exceed `10.0`; the leash/vanilla physics may still move the anchor.

## Event coverage

Listeners use Bukkit's default event priority (`NORMAL`) and do not specify `ignoreCancelled`, unless the event type itself dictates otherwise.

### `EntityDamageEvent`

Cancels any damage to a parrot carrying the feature scoreboard tag. It does not explicitly cancel damage to the armor stand, although the stand is invulnerable.

### `PlayerTeleportEvent`

When an active definition exists, calls `handleTeleport(player)` immediately during the event. The handler reads the player's current location/world at that time and removes the old entities, then schedules recreation ten ticks later.

Important ordering detail: the listener does not use `event.getTo()` and is not delayed before the initial remove. The delayed `summon()` uses `player.getLocation()` after ten ticks, which normally reflects the destination. The event is not configured with `ignoreCancelled = true`; a cancelled teleport can still trigger removal and delayed recreation.

### `PlayerQuitEvent` and `PlayerDeathEvent`

Call `removeBalloon()`, which spawns five cloud particles above the stand before removing both entities and active state.

### `PlayerLeashEntityEvent`

Cancels **all** attempts by a player with an active balloon to leash an entity, not only attempts involving the balloon parrot. This prevents the active player's leash action from creating another leash/hitch while the balloon is present.

### `PlayerUnleashEntityEvent`

Cancels unleashing when the event entity is a tagged balloon parrot.

### `PlayerInteractAtEntityEvent`

Cancels right-click interaction with a tagged balloon armor stand.

### `EntityMountEvent`

When the mounting entity is a player, removes their balloon. This occurs for any mount/vehicle type.

There is no explicit world-change event beyond teleport handling, no chunk-unload listener, no entity-remove reconciliation listener, and no join restore because state is not persistent.

## Teleport recreation ordering

`handleTeleport`:

1. reads the active definition;
2. reads the armor stand and copies its helmet when available;
3. calls public `removeBalloon(player)`, including cloud particles and removal from all maps;
4. schedules a ten-tick delayed task;
5. the delayed task summons using the captured helmet, or rebuilds from the definition when helmet was unavailable;
6. restores `active` after summoning.

The delayed callback does not recheck that the player is still online, alive, in a valid world, not mounted, still permitted, or that the feature has not created a newer balloon in the meantime. Lifecycle cancellation protects feature disable, but quit/death/mount occurring during the ten-tick window can race with delayed recreation unless their event removes an already-present map entry. This is an important test case and a likely future hardening area.

## Removal semantics

Public `removeBalloon(player)` returns false when the UUID has no parrot entry, even if a stale stand or active definition exists. When a parrot entry exists:

- removes stand map entry;
- spawns cloud particles when the stand is live;
- removes stand;
- removes parrot map entry and entity;
- removes active definition;
- returns true.

Internal removal skips particles and removes whatever entries/entities are present. It is used for offline cleanup and shutdown.

Because the parrot map is treated as the public ownership indicator, partial map/entity corruption can leave an orphan stand that public removal does not reach. The tether loop also skips null pairs rather than reconciling them.

## Persistence, database and messaging

Balloons is fully ephemeral:

- no selected balloon is written to disk/database;
- no DataProvider slot exists;
- no Redis message is published/subscribed;
- no cross-server restore occurs;
- no API service is registered;
- no PlaceholderAPI expansion exists.

Switching backend, disconnecting, dying, or restarting removes the cosmetic. Players must select it again.

## Resource-pack behaviour

Item definitions with custom-model data require clients to have a compatible resource pack for the intended model. Without it, the underlying material is displayed. The feature does not distribute or verify a resource pack and does not react to pack acceptance status.

Head definitions embed the supplied Base64 texture directly into a new random-UUID Paper profile each time an item is created. Menu icon creation and entity creation can therefore produce separate profile UUIDs with the same texture.

## Lifecycle

Initialization order:

1. construct `BalloonRegistry` and open `local/balloons.yml`;
2. load definitions;
3. construct `BalloonsHandler`, scheduling the two-tick tether task;
4. register `BalloonsListener`;
5. register `/balloons`.

Disable calls `handler.shutdown()`, which iterates current parrot keys and performs internal removal without particles. Framework lifecycle cleanup cancels the repeating/delayed tasks and unregisters command/listener/GUI resources.

The registry has no explicit close action because its config view is managed by the global configuration service.

## Developer source map

- Feature lifecycle/default messages: `features/balloons/Balloons.java`
- Definition loading: `features/balloons/registry/BalloonRegistry.java`
- Definition/item construction: `features/balloons/model/BalloonDefinition.java`
- Runtime entities/tethering: `features/balloons/internal/BalloonsHandler.java`
- Event wiring: `features/balloons/listener/BalloonsListener.java`
- Paged GUI: `features/balloons/menu/BalloonsMenu.java`
- Command: `features/balloons/command/BalloonsCommand.java`
- Follow physics: `features/balloons/util/Distance.java`
- Definition and distance tests: `src/test/.../features/balloons/`

## Operational verification

1. Configure at least one valid material balloon, one custom-model balloon, one head balloon, and one locked balloon.
2. Verify child order controls menu order and page navigation after 21 entries.
3. Test shared permission, per-definition permission, locked replacement icon, and the runtime recheck in `setBalloon`.
4. Activate, switch, and remove balloons through both GUI and command.
5. Verify `/balloons remove` while mounted returns the vehicle warning rather than removing.
6. Walk, sprint, fly, fall, cross chunks, exceed 6/10 blocks from the anchor, and observe tether recovery.
7. Teleport within a world, across worlds, through portals, and via cancelled teleports; inspect the ten-tick recreation path.
8. Quit, die, and mount during the ten-tick teleport delay to test for delayed resurrection/orphans.
9. Try damaging/unleashing/interacting with the tagged entities and verify protection.
10. While a balloon is active, attempt to leash an unrelated mob and confirm the broad cancellation behaviour.
11. Remove/kill the parrot or stand administratively and observe partial-state recovery limitations.
12. Reload/disable the feature with multiple active balloons and verify all tagged entities are removed.
13. Test without the resource pack and with malformed material/head/custom-model configuration.

## Troubleshooting

- **Menu says no balloons/config warning:** `local/balloons.yml` has no direct children under `balloons`, or a config-only reload did not rebuild the registry.
- **Balloon displays as plain item:** the client lacks the matching resource-pack custom model or `custom_model_data` is absent/non-positive.
- **Head is default/invalid:** verify Base64 texture data and ensure the definition has no valid `item`, because item mode takes precedence.
- **Balloon disappears on mount:** intentional; `EntityMountEvent` removes it.
- **Cannot leash other mobs:** intentional current behaviour while a balloon is active; the listener cancels the player's entire leash event.
- **Balloon reappears after quit/death shortly after teleport:** inspect the ten-tick delayed recreation race.
- **Detached/orphan entity:** check whether one member of the parrot/stand pair was externally removed. Current reconciliation skips incomplete pairs rather than rebuilding them.
- **Balloon state is lost after server switch/restart:** expected; there is no persistence or proxy messaging.
- **Console command unexpectedly reloads config:** current non-player command path calls `reloadConfig()` before returning the player-only message; it does not reload definitions.
