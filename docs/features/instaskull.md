# InstaSkull

> Paper · Feature name `InstaSkull` · feature package `features.instaskull` · disabled by default

InstaSkull makes player-head blocks break instantly for permitted players by setting Paper/Bukkit's `BlockDamageEvent` insta-break flag. It does not create heads, look up player profiles, change textures, alter drops, handle deaths, give items, manage cooldowns, or persist any data.

## Commands and permission

No command is registered.

Permission:

```text
serverfeatures.feature.instaskull.use
```

Without permission, the listener returns and normal block-breaking behaviour applies.

## Complete configuration reference

File: `plugins/ServerFeatures/features/InstaSkull/config.yml`.

| Key | Default | Meaning |
|---|---:|---|
| `enabled` | `false` | Registers the one block-damage listener. |

There are no configurable materials, drops, worlds, claims, tools, cooldowns, profile settings, sounds, particles or bypass rules.

## Event contract and ordering

```java
@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onBlockDamage(BlockDamageEvent event)
```

Flow:

1. check use permission;
2. inspect damaged block material;
3. when material is `PLAYER_HEAD` or `PLAYER_WALL_HEAD`, call `event.setInstaBreak(true)`;
4. otherwise do nothing.

Events cancelled before `HIGHEST` are ignored. Protection plugins cancelling block damage at lower priorities therefore prevent instant break. Same-priority ordering is unspecified.

The feature does not cancel the event and does not directly set the block to air. Final block removal, drop generation, block-break events, enchantment/tool behaviour and protection remain Paper/vanilla/other-plugin responsibilities after the insta-break flag is set.

## Supported blocks

Only:

- `Material.PLAYER_HEAD`
- `Material.PLAYER_WALL_HEAD`

Not included:

- skeleton/wither skeleton/zombie/creeper/dragon/piglin heads;
- custom blocks represented by other materials;
- item-form heads in inventories;
- armor stands/entities wearing heads.

The block's stored profile/texture metadata is not read or changed.

## Drop and protection semantics

Because no custom drop is created:

- normal player-head block drops should preserve ordinary skull metadata according to Paper;
- plugins modifying `BlockBreakEvent`/drops continue to control the outcome;
- no duplication rollback issue exists inside this feature;
- claims/WorldGuard are respected only when they cancel the relevant damage/break path; InstaSkull has no direct integration query;
- a later plugin can still prevent or modify the final break.

`BlockDamageEvent#setInstaBreak` requests immediate completion; it is not a guarantee that every later plugin permits the break.

## Threading and performance

The event runs synchronously on the server thread. Work is constant-time: one permission check and material comparison. No tasks, profile lookups, network calls, database work or state maps exist.

## Persistence, database and messaging

None. InstaSkull has no DataProvider/database, Redis/proxy messaging, API, PAPI expansion or local data store.

## Lifecycle

Initialization registers `SkullBreakListener`. Disable is empty; feature lifecycle unregisters the listener.

## Developer source map

- Lifecycle/default config: `features/instaskull/InstaSkull.java`
- Event logic: `features/instaskull/listener/SkullBreakListener.java`
- Metadata: `features/instaskull/meta/Meta.java`

## Operational verification

1. Test floor and wall player heads with/without permission.
2. Test every non-player mob-head type and verify normal breaking remains.
3. Test protection plugins cancelling at lower/same/later priorities.
4. Verify custom player textures/profile metadata survive the normal drop.
5. Test survival/creative and different tools/enchantments.
6. Verify other drop-modifying plugins still receive the normal break/drop path.
7. Disable/re-enable and confirm only instant-break behaviour changes.

## Troubleshooting

- **Feature does not create skulls from deaths:** that functionality does not exist.
- **Mob heads are not instant:** only player head and player wall head materials are supported.
- **Protected head does not break:** expected when another plugin cancels the event/break.
- **Texture/drop is wrong:** inspect Paper/other drop/profile plugins; InstaSkull never edits metadata or drops.
- **Permission seems ignored:** verify exact node and whether another plugin independently instant-breaks heads.
