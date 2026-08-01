# Vanish

> Paper · Feature ID `vanish` · disabled by default · persisted MySQL state + per-viewer visibility + durable proxy updates

Vanish owns a local set of vanished online UUIDs, persists each player's preference by canonical DataRegistry numeric ID, hides vanished players from unauthorized viewers, applies interaction protections, publishes versioned durable state updates to ProxyFeatures and exposes local API/PlaceholderAPI counts.

The feature also forces vanished players into `SPECTATOR`, optionally sets Bukkit's invisible flag and disables collisions. Current removal behavior does not restore the previous game mode and has an important permission-dependent early return described below.

## Commands and permissions

Command: `/vanish`

| Syntax | Permission | Behavior |
|---|---|---|
| `/vanish` | `serverfeatures.feature.vanish.command.vanish.toggle` | Toggle self. Player-only. |
| `/vanish on` / `/vanish off` | same | Set self explicitly. |
| `/vanish <player>` | `serverfeatures.feature.vanish.command.vanish.others` | Toggle an online target. |
| `/vanish <player> on|off` | same | Set an online target explicitly. |

Other permission:

| Permission | Effect |
|---|---|
| `serverfeatures.feature.vanish.see` | Viewer can see vanished players and bypasses new-viewer hide processing. |

The self-toggle permission is also used as the feature's definition of “staff who receive vanish notifications.” It additionally gates `removeVanish` cleanup, which has broader consequences below.

### Command parsing details

- Console can use only the other-player form.
- Target lookup uses `Bukkit.getPlayer`, not exact lookup, so Paper's partial-name resolution can match a player.
- More than two arguments are not rejected: the first is target, the second is interpreted when it is `on`/`off`, otherwise the target is toggled; later arguments are ignored.
- Self explicit state reports `vanish.already_state` without changing/publishing.
- Other-player operation informs actor, target and other online players with self-toggle permission.
- Console is represented as `Console` in staff broadcasts but `sender.getName()` in the target notification.

Tab completion always suggests `on`, `off` and every online player name for argument one, regardless of sender permissions or vanish visibility. Argument two suggests `on`/`off`. This command completer can therefore reveal vanished names independently of the generic `TabCompleteEvent` filter.

## Configuration

| Key | Default | Behavior |
|---|---:|---|
| `enabled` | `false` | Initializes ORM, service/API, commands, listeners, tasks, placeholder and optional Redis. |
| `set_invisible_flag` | `true` | Calls `Player#setInvisible(true)` on vanish and false on removal when the feature observes the opposite current flag. |
| `disable_collisions` | `true` | Calls `setCollidable(false)` on vanish and true on removal. |
| `prevent_item_pickup` | `true` | Cancels item and arrow pickup for vanished players. |
| `prevent_damage_and_interact` | `true` | Cancels damage to/from vanished players and right-click entity interaction to/from vanished players. |
| `prevent_entity_targeting` | `true` | Cancels entity targeting when target is vanished. |
| `filter_tab_completion` | `true` | Removes exact vanished player-name completions for viewers without `see`. |
| `actionbar_interval_ticks` | `40` | Repeat interval for vanished actionbar; clamped to at least 5 ticks at initialization. |
| `stream` | `proxy.vanish.update` | Durable Redis stream; blank falls back to default. |
| `publish_retry_attempts` | `3` | Total durable publish attempts; must be positive or falls back to 3. |
| `publish_retry_delay_millis` | `250` | Fixed retry scheduling delay; must be positive or falls back to 250. |

Boolean interaction/flag settings are directly cast during runtime use. Wrong types can throw in event/service paths. Redis numeric validation logs and falls back.

`server_name` comes from global configuration, defaults to `server` for messaging, and is captured at EventBus construction.

## Persistence schema and identity

ORM connection:

```text
MYSQL logical connection player_data_rw
registered alias ormConnection
```

Table/entity:

```sql
player_vanish (
    player_id BIGINT PRIMARY KEY,
    vanished BOOLEAN NOT NULL
)
```

`player_id` is the canonical DataRegistry player ID, not UUID text. `VanishRepository` resolves identities through `PlayerIdentityResolver` and queries/upserts one row per player.

### Write path

After a local state change, when persistence is enabled:

1. read cached numeric ID from `playerIds`;
2. if absent, synchronously ask DataRegistry for an existing active identity by UUID;
3. cache the numeric ID when found;
4. schedule asynchronous ORM upsert;
5. if no identity exists, log a warning and continue local/messaging state without persistence.

The command reports success before async persistence completes. ORM exceptions inside the scheduled runnable are not handled in `setVanishedInternal` itself; lifecycle task logging determines visibility.

### Join restore

`VisibilityListener` handles join at `MONITOR` and uses `DataRegistryIdentityGate.runWhenReady`. Once ready:

1. cache numeric ID;
2. asynchronously query `player_vanish`;
3. schedule main-thread `applyJoinState`;
4. re-resolve player by UUID and require online state.

The feature also calls `bootstrapOnlinePlayers()` after Redis initialization for players already online during feature enable/reload. That direct path first tries synchronous active-ID resolution, then delegates to the same async persisted-state query.

Restored vanished state is applied with `persist=false` but still publishes to Redis because publication is outside the persistence condition. This ensures proxy state is rebuilt after backend feature startup.

When no identity is found in the direct bootstrap path, the feature applies non-vanished state immediately.

## Runtime state

`VanishService` stores concurrent:

- `vanished: Set<UUID>`;
- `playerIds: Map<UUID,Long>`.

`allVanished()` returns an unmodifiable **live view** of the concurrent set, while snapshot methods create immutable copies.

Quit removes UUID from both maps but does not change the persisted row and does not publish an explicit false state. Persistence therefore represents the desired preference for next join, while local set represents currently online vanished players.

## Applying vanish

On transition false → true:

1. add UUID to vanished set;
2. reconcile every online viewer/target pair:
   - vanished target + viewer lacks `see` → `hidePlayer(plugin,target)`;
   - otherwise → `showPlayer(plugin,target)`;
3. when configured, set target non-collidable;
4. when configured and not already invisible, set invisible true;
5. set game mode to `SPECTATOR` (exceptions swallowed);
6. asynchronously persist when requested;
7. publish durable proxy state.

The service does not capture previous game mode, collision state or invisibility ownership.

### Visibility model

Visibility is per viewer through Bukkit plugin-scoped `hidePlayer`/`showPlayer` calls. Self pairs are skipped.

A viewer with `serverfeatures.feature.vanish.see` is explicitly shown the target during pair updates. A new viewer without that permission is hidden from every currently vanished online player.

Other plugins can independently call hide/show using their plugin identity. Effective client visibility is the combination of all plugin-scoped states; Vanish only owns calls made with the ServerFeatures plugin instance.

## Removing vanish and restoration limitations

On transition true → false, `setVanishedInternal` removes UUID from the logical set and then calls `removeVanish`.

The first line of `removeVanish` is:

```text
if target does not have serverfeatures.feature.vanish.command.vanish.toggle:
    return
```

Therefore, when the target lacks the self-toggle permission:

- logical vanished state is removed;
- persistence can be written false;
- proxy false can be published;
- command success messages can be sent;
- **but viewers are not shown and invisible/collision flags are not restored**.

This can occur when staff vanish another player without granting self-toggle permission, when a permission is revoked while vanished, or during disable cleanup after permission changes.

When cleanup does run, it:

1. calls `showPlayer` for every online viewer;
2. sets collidable true if configured and currently false;
3. sets invisible false if configured and currently true.

It never changes game mode. A player placed into `SPECTATOR` by vanish remains spectator after `/vanish off` unless another feature/staff action changes it.

It also cannot distinguish feature-owned flags from pre-existing state:

- a player already invisible for another reason is made visible on removal;
- a player intentionally non-collidable before vanish is made collidable;
- previous game mode is lost.

These are critical implementation facts for operational use and future redesign.

## Persisted join behavior

When persisted vanished is true:

- normal vanish application already sets spectator;
- a second task two ticks later sets spectator again when still online/vanished;
- other staff with self-toggle permission receive `staff_joined_vanished`;
- the joining player's viewer visibility is reconciled.

When false:

- UUID is removed;
- `removeVanish` is called, including its self-toggle permission gate;
- new-viewer visibility is applied.

A player without self-toggle permission who previously retained stale hidden/invisible flags can therefore also skip restoration on a non-vanished join-state path.

## Respawn and world-change reconciliation

### World change

At `MONITOR`, cancelled events ignored:

- apply all vanished targets to the moving player as viewer;
- explicitly update pair visibility for each vanished player.

The two loops overlap in purpose but reinforce viewer state.

### Respawn

At `MONITOR`:

- update the respawning viewer against all vanished targets;
- if the respawning player is vanished, call `setVanished(player,true)`.

Because `setVanishedInternal` returns immediately when current state already equals true, this call does not reapply flags/game mode/visibility to the vanished target. If respawn resets a relevant property, the current same-state short circuit prevents repair.

## Interaction protections and event ordering

All interaction handlers run at `LOW` with `ignoreCancelled=true`.

| Event | Cancellation rule |
|---|---|
| `EntityDamageByEntityEvent` | Cancel when victim or direct player damager is vanished. Projectile shooter ownership is not checked unless Bukkit damager itself is player. |
| `PlayerInteractEntityEvent` | Cancel when right-clicked player is vanished or actor is vanished. |
| `EntityTargetLivingEntityEvent` | Cancel when target player is vanished. |
| `EntityPickupItemEvent` | Cancel when picking entity is vanished player. |
| `PlayerPickupArrowEvent` | Cancel when player is vanished. |

Not covered include block interaction/break/place, inventory use, projectile launch, potion effects, pressure plates, chat/commands and non-player right-click target visibility beyond the actor rule.

At LOW, later plugins can uncancel/replace behavior. Already-cancelled events are skipped.

## Tab-completion filtering

`TabCompleteEvent` runs at `LOWEST`, cancelled events ignored.

For player senders without `see`, each completion is removed only when:

1. the complete suggestion string exactly resolves through `Bukkit.getPlayerExact`;
2. that player is in local vanished state.

Suggestions containing additional syntax, selectors, nicknames or partial player text are not removed. Since priority is LOWEST, later completion providers can add vanished names after this filter. Brigadier/client suggestion paths that do not surface through this event are outside the listener.

Players with `see` and non-player senders bypass filtering.

The `/vanish` command's own completer also lists online names without consulting this filter/permission.

## Actionbar

A synchronous repeating task starts after one interval and repeats at the clamped interval. For every vanished UUID it re-resolves online player and sends localized `vanish.actionbar`.

Errors per player are swallowed; the outer tick also logs only the top-level error message. There is no config switch besides disabling the feature or using an enormous interval.

## Durable Redis publication

Redis is optional. MySQL/local vanish remains operational when provider initialization fails.

Provider:

- key `redis`;
- namespace `hauntedmc`;
- durable data access;
- configured stream, default `proxy.vanish.update`.

Payload is shared ProxyFeatures `VanishStateMessage` containing:

- UUID string;
- player name (empty when null);
- vanished boolean;
- backend server name;
- monotonic state version.

The message's durable key is used as both event ID and processing key.

### State version

Each feature-process EventBus starts an `AtomicLong(0)`. Next version is:

```text
max(previous + 1, currentTimeMillis)
```

This provides monotonic ordering inside one backend process and time-based progression across ordinary restarts. Consumers should still fence by the shared contract's player/server/version semantics.

### Retry behavior

Publication is asynchronous and not awaited by commands/state changes.

- attempt immediately;
- on failure before final attempt, log warning and schedule async retry after fixed delay;
- after configured total attempts, log severe with root cause and complete future exceptionally;
- scheduling failure completes exceptionally.

A newer local state can be published while an older state is retrying. Messages can arrive out of order; the version exists so ProxyFeatures can reject stale state.

There is no local outbox/disk persistence. A process shutdown before successful Redis append loses the unpublished update.

## API

`VanishAPI` is registered through the feature API manager and exposes:

```java
Set<UUID> getVanishedPlayers(); // live unmodifiable view
int getVanishedCount();
boolean isVanished(UUID uuid);
```

NotifyLogin and other local features resolve this service through `FeatureServices`. Consumers should treat it as online/local backend state, not network-global persistence.

## PlaceholderAPI

When PlaceholderAPI is present, persistent expansion identifier `vanish` registers:

```text
%vanish_playercount%
```

It returns:

```text
max(0, Bukkit online count - local vanished set size)
```

The `OfflinePlayer` argument is ignored. Any throwable returns `0`.

It is local backend count, not proxy/network count. During join restore latency a persisted vanished player can temporarily be counted visible; stale logical/flag divergence can also make the arithmetic differ from actual viewer visibility.

## Stateful feature reload

Vanish implements `StatefulFeature<ReloadSnapshot>`.

Capture returns a snapshot only when the vanished set is non-empty, containing:

- vanished UUID copy;
- player-ID map copy.

If no one is vanished, player-ID cache alone is not snapshotted.

Restore:

- merges restored player IDs;
- for each snapshotted UUID still online, adds state and reapplies vanish;
- reconciles every online viewer.

Normal initialization/bootstrap/persistence can run around snapshot restoration; idempotence mostly relies on set membership and same-state checks.

## Disable cleanup

`cleanupOnDisable` iterates a copy of vanished UUIDs and calls `removeVanish` for online players, then clears both maps.

Because `removeVanish` is permission-gated and does not restore game mode, disable does not guarantee complete presentation/state restoration. It also does not publish false states or persist false; persisted preferences intentionally remain for next enable/join.

Lifecycle managers own listener/task/data/placeholder resource cleanup.

## Messages and notification audiences

Command/self/other messages are defined in defaults. Staff broadcasts go to online players with **self-toggle permission**, excluding the actor UUID where supplied. The message builder for staff toggle supplies both `{actor}` and `{target}`, though default `staff_enabled/disabled` uses only `{target}`.

`staff_joined_vanished` excludes the joining vanished player.

## Important implementation boundaries

- Vanish always sets spectator and never restores previous game mode.
- Removal/disable visual cleanup requires the target to currently hold self-toggle permission.
- Previous invisible/collision state is not preserved.
- Same-state calls do not reapply drifted flags.
- Persistence writes are asynchronous after command success.
- Redis is optional and asynchronous, with out-of-order retry potential.
- Quit removes local state but leaves persisted preference true.
- No false Redis update is sent on quit/disable.
- Tab filtering is exact-name, LOWEST and incomplete for command-specific suggestions.
- Staff audiences are based on toggle permission, not `see`/others.
- Interaction protection is partial and LOW priority.
- Placeholder/API counts are local online state.
- `allVanished`/API player set is a live view.

## Verification checklist

1. Toggle from survival/creative/adventure and verify spectator remains after off.
2. Vanish another player without self-toggle permission, then unvanish/disable and inspect hide/invisible/collision flags.
3. Revoke toggle permission while vanished and repeat cleanup.
4. Start already invisible/non-collidable and inspect ownership loss after off.
5. Persist true, relog/reload and verify DataRegistry/ORM restore plus two-tick spectator task.
6. Change world/respawn and inspect pair visibility and same-state reapplication gaps.
7. Test every interaction event plus projectiles, blocks and inventories outside coverage.
8. Exercise generic and `/vanish` tab completion with/without `see` and later completion providers.
9. Stop MySQL during read/write and compare local, persisted and command feedback.
10. Stop Redis, trigger rapid true/false transitions and inspect retries/version ordering at ProxyFeatures.
11. Verify `%vanish_playercount%`, VanishAPI and PlayerCount during join/quit restore windows.
12. Reload/disable with multiple viewers and competing hide/show plugins.

## Source map

- Defaults/lifecycle/ORM/Redis/snapshot: `features/vanish/Vanish.java`
- Runtime state/visibility/flags/persistence/publication: `features/vanish/internal/VanishService.java`
- Command/permissions/completion: `features/vanish/command/VanishCommand.java`
- ORM repository/schema: `features/vanish/internal/VanishRepository.java`, `entities/PlayerVanishEntity.java`
- Visibility lifecycle: `features/vanish/listener/VisibilityListener.java`
- Protections: `features/vanish/listener/InteractionListener.java`
- Completion filter: `features/vanish/listener/TabListener.java`
- Durable publisher: `features/vanish/internal/messaging/EventBusHandler.java`
- Public API: `features/vanish/internal/VanishAPI.java`
- Placeholder: `features/vanish/internal/VanishPlaceholder.java`
