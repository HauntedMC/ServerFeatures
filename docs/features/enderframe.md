# EnderFrame

> Paper · Feature name `EnderFrame` · feature package `features.enderframe` · disabled by default

EnderFrame allows permitted players to instantly pick up End Portal Frame blocks outside natural strongholds and to place those frames outside natural strongholds. Picking up a frame drops one `END_PORTAL_FRAME` item and clears nearby active `END_PORTAL` blocks on the same Y level.

Break/pickup checks optionally consult WorldGuard and GriefPrevention. Placement does **not** consult either integration despite the listener class comment; it only checks the feature permission and Paper stronghold structure data.

## Commands and permission

No command is registered.

Permission:

```text
serverfeatures.feature.enderframe.use
```

Permission semantics differ by event:

- **Damage/pickup:** without permission, the feature returns and leaves vanilla behaviour untouched. End Portal Frames are normally unbreakable, so the player simply cannot pick them up through this feature.
- **Placement:** without permission, the `BlockPlaceEvent` is explicitly cancelled with no feature-specific feedback.

There is no separate break/place/admin bypass permission.

## Complete configuration reference

File: `plugins/ServerFeatures/features/EnderFrame/config.yml`.

| Key | Default | Exact behaviour and edge cases |
|---|---:|---|
| `enabled` | `false` | Detects optional integrations and registers pickup/place listeners. |
| `pickup_radius` | `5` | Inclusive X/Z square radius used **only after a successful pickup** to clear `END_PORTAL` blocks at the picked frame's Y level. It is not used for stronghold or protection checks. Directly cast to `int` on pickup. |

The clear area is a square of `(2r + 1) × (2r + 1)` blocks, not a circle. At default radius 5, up to 121 positions are inspected.

- `0`: checks only the frame's X/Z coordinate at the same Y.
- Negative: loop start becomes greater than loop end and no portal blocks are cleared.
- Very large values synchronously scan many blocks and can load/access chunks through `World#getBlockAt`; use conservative values.

There are no settings for item amount, allowed worlds, protection plugins, structure type, portal clearing Y range, drops, sounds, particles, permissions or event priority.

## Optional integration detection

During feature initialization:

```java
griefPreventionEnabled = Bukkit.getPluginManager().isPluginEnabled("GriefPrevention")
worldguardEnabled = Bukkit.getPluginManager().isPluginEnabled("WorldGuard")
```

These booleans are snapshots. Enabling/disabling an integration later does not update them without reloading EnderFrame.

The feature has compile/runtime references to both APIs in `BlockBreakListener`. Although checks are guarded by the booleans, class loading still requires compatible dependency availability according to the plugin's dependency/shading setup. Test startup both with and without optional plugins.

## Natural stronghold detection

`LocationUtils.isInStronghold(block)`:

1. require world environment `NORMAL`;
2. call Paper `World#hasStructureAt(Position.block(location), Structure.STRONGHOLD)`.

Consequences:

- Nether/End/custom environments always return false, even if a custom generator places stronghold-like structures.
- Detection uses Paper's structure reference at the exact block position, not proximity/radius or visual block pattern.
- Player-placed End Portal Frames inside the bounding/reference area of a natural stronghold are also protected.
- A copied/rebuilt stronghold outside structure metadata is not protected.
- This is Paper-specific API; non-Paper compatibility is not provided.

## Pickup event contract

The feature uses `BlockDamageEvent` rather than `BlockBreakEvent` so it can mark the frame for instant breaking.

```java
@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
```

Flow:

1. require block type `END_PORTAL_FRAME`;
2. require use permission; otherwise return without cancellation;
3. deny natural stronghold location;
4. when WorldGuard was detected, deny when `RegionQuery#testBuild` returns false;
5. when GriefPrevention was detected, deny when claim `allowBreak(player, material)` returns a non-null reason;
6. call `event.setInstaBreak(true)`;
7. immediately drop one End Portal Frame one block above the frame;
8. send pickup-success message;
9. scan/clear nearby `END_PORTAL` blocks on the same Y.

### Critical block-removal semantics

The handler does **not** explicitly set the End Portal Frame block to air and does not cancel the event. It relies on `BlockDamageEvent#setInstaBreak(true)` causing Paper/vanilla to complete the actual block break after the event.

The custom item is dropped immediately during `HIGHEST`, before final break completion. If another later listener cancels/prevents the resulting break or Paper behaviour differs, the item can be dropped while the frame remains. There is no item-drop rollback or duplication guard.

The handler also does not suppress vanilla drops through `BlockDropItemEvent`/`setDropItems`; End Portal Frames normally do not drop, but another plugin changing drop behaviour could create an additional item.

### Portal clearing timing

Nearby portal blocks are set to air immediately during `BlockDamageEvent`, before confirmation that the frame actually broke. The scan:

- includes both endpoints in X and Z;
- uses exactly `location.getBlockY()`;
- checks only `Material.END_PORTAL`;
- calls `setType(Material.AIR)` with default physics behaviour;
- performs no WorldGuard/GriefPrevention permission checks for each cleared portal block;
- does not fire a player block-break event for those direct mutations.

A successful permission check for the frame location can therefore clear portal blocks across adjacent region/claim boundaries inside the configured square.

## WorldGuard pickup check

When the startup flag is true:

1. retrieve plugin named `WorldGuard` and cast to `WorldGuardPlugin`;
2. if lookup returns null, treat as allowed;
3. wrap player to `LocalPlayer`;
4. adapt Bukkit location through WorldEdit;
5. create `RegionQuery`;
6. call `query.testBuild(location, localPlayer)`;
7. deny when false.

This tests the frame location only. It does not test the drop position or every portal-clearing position.

WorldGuard API/runtime incompatibility is not caught inside the event handler and can propagate as an event error.

## GriefPrevention pickup check

When startup flag is true:

```java
Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, false, null)
```

- no claim: allowed;
- claim present: call `claim.allowBreak(player, END_PORTAL_FRAME)`;
- non-null denial reason: cancel and send generic claim-restricted message.

The returned GriefPrevention reason text is not shown. The check does not call container/build/place trust variants or inspect the nearby portal-clear square.

## Placement event contract

```java
@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onBlockPlace(BlockPlaceEvent event)
```

Flow:

1. require resulting block type `END_PORTAL_FRAME`;
2. without permission, cancel silently;
3. when exact position is inside a Paper stronghold, cancel and send `stronghold_place_restricted`;
4. otherwise leave placement allowed.

Despite source comments, placement performs **no explicit WorldGuard or GriefPrevention query**. Normal protection plugins usually cancel `BlockPlaceEvent` before `HIGHEST`, and `ignoreCancelled = true` respects that. However, EnderFrame does not independently validate optional integrations or neighboring portal effects for placement.

The item orientation/eye property is ordinary vanilla block data and is not modified.

## Event ordering and protection boundaries

Both listeners run at `HIGHEST` and ignore already-cancelled events.

- Lower-priority protection cancellation prevents EnderFrame actions.
- Same-priority ordering with other plugins is unspecified.
- `MONITOR` listeners see EnderFrame cancellation/insta-break status and any already-cleared portal blocks.
- Pickup's direct drop and portal mutations occur before final event outcome and are not transactional.

No explosion, piston, WorldEdit, command, creative middle-click, structure-generation or direct block-set path is covered. Those mechanisms can move/remove/place frames unless separately protected.

## Messages and variables

| Key | Variables | Use |
|---|---|---|
| `enderframe.pickup_success` | none | Sent after custom drop and insta-break request. |
| `enderframe.claim_restricted` | none | GriefPrevention pickup denial. |
| `enderframe.stronghold_restricted` | none | Pickup denial in natural stronghold. |
| `enderframe.worldguard_restricted` | none | WorldGuard pickup denial. |
| `enderframe.stronghold_place_restricted` | none | Placement denial in natural stronghold. |

No message is sent for missing permission or an event already cancelled by another plugin.

## Item and portal behaviour

The drop is a plain:

```java
new ItemStack(Material.END_PORTAL_FRAME)
```

It has no PDC marker, owner, lore, custom model or provenance. Once obtained, it is indistinguishable from any other End Portal Frame item.

The feature does not create an End Portal when frames are placed/filled. Vanilla portal creation mechanics determine whether a player-built ring activates. Pickup clears nearby active portal blocks to prevent leaving a floating portal plane after frame removal.

## Performance and chunk access

Stronghold structure lookup and region/claim queries occur synchronously in the damage/place event.

The portal clear performs nested synchronous world block lookups. A large `pickup_radius` can be expensive and may touch chunks outside the loaded local area. No chunk-loaded guard or asynchronous scan is used; Bukkit world mutation must remain on the main thread.

## Persistence, database and messaging

EnderFrame has no DataProvider/database, Redis, proxy messaging, API, PAPI expansion, custom data file or player state. The only durable outcomes are ordinary world blocks/items saved by Paper.

## Lifecycle

Initialization:

1. snapshot GriefPrevention enabled state;
2. snapshot WorldGuard enabled state;
3. register pickup listener;
4. register placement listener.

Disable is empty. Lifecycle cleanup unregisters listeners. Existing frame items/placed blocks remain.

## Developer source map

- Defaults/integration flags/lifecycle: `features/enderframe/EnderFrame.java`
- Pickup/protection/portal clearing: `features/enderframe/listener/BlockBreakListener.java`
- Placement: `features/enderframe/listener/BlockPlaceListener.java`
- Stronghold lookup: `features/enderframe/util/LocationUtils.java`
- Location tests: `src/test/.../features/enderframe/util/LocationUtilsTest.java`
- Metadata: `features/enderframe/meta/Meta.java`

## Operational verification

1. Test permission/no-permission pickup and placement.
2. Pick up frames outside/inside exact Paper stronghold structure metadata in normal world.
3. Test Nether/End/custom worlds.
4. Verify WorldGuard and GriefPrevention pickup denial and ordinary placement-event protection.
5. Place a frame on a claim/region boundary and inspect portal clearing across boundaries.
6. Configure radius 0, negative, default and large values; verify square same-Y semantics and performance.
7. Add a later listener that cancels damage/break and check for custom-drop/portal-clear duplication inconsistency.
8. Test vanilla/custom drops and ensure no double frame item.
9. Test top/bottom active portal plane levels to confirm only exact Y is cleared.
10. Enable/disable WorldGuard/GP after feature initialization and confirm flags require reload.
11. Test explosions/pistons/WorldEdit separately because those paths are not handled.

## Troubleshooting

- **Pickup item appears but frame remains:** another later plugin may block final insta-break; custom drop happens before final outcome.
- **Portal blocks disappear in another claim/region:** only frame location is checked; clear square has no per-block protection query.
- **Placement ignores explicit GP/WG messages:** placement relies on normal cancelled `BlockPlaceEvent`; it has no dedicated integration checks/messages.
- **Stronghold-like build is not protected:** detection uses Paper structure metadata, not block patterns.
- **Frame in Nether can be picked up:** stronghold detection always returns false outside `NORMAL` environment.
- **Radius seems circular expectation:** implementation scans an inclusive square at one Y level.
- **Feature errors without optional plugin:** verify dependency/class-loading/API compatibility, not only runtime enable flags.
