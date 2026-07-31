# NotifyLogin

> Paper · Feature name `NotifyLogin` · feature package `features.notifylogin` · disabled by default

NotifyLogin broadcasts one local join announcement when the joining player has the hard-coded Supreme+ permission and is not currently vanished according to the enabled ServerFeatures Vanish API. Every local online player receives the localized announcement; there is no recipient permission, delay, watched-player list, group configuration, world/server filter, database or Redis integration.

## Commands and permissions

No command is registered.

Trigger permission:

```text
serverfeatures.feature.notifylogin.supremeplus
```

A joining player with this permission is eligible for announcement. It is not a recipient permission: all local online players receive the message.

There is no bypass, staff-only audience, per-player toggle or admin command.

## Complete configuration reference

File: `plugins/ServerFeatures/features/NotifyLogin/config.yml`.

| Key | Default | Meaning |
|---|---:|---|
| `enabled` | `false` | Registers the join listener. |

No watched identities/groups, recipient permission, delay, server/world filters, message mode or vanish policy settings exist. Previous documentation suggesting them was inaccurate.

## Join event contract

```java
@EventHandler
public void onPlayerJoin(PlayerJoinEvent event)
```

Uses Bukkit defaults:

- priority `NORMAL`;
- no cancellation setting (join is not normally cancellable).

Flow:

1. get joining player;
2. require `serverfeatures.feature.notifylogin.supremeplus`;
3. resolve VanishAPI through `FeatureServices.find`;
4. if API exists and reports joining UUID vanished, return;
5. otherwise iterate `Bukkit.getOnlinePlayers()`;
6. build `notifylogin.supremeplus` separately for each recipient audience;
7. inject joining Bukkit name as `{name}`;
8. send component.

The normal vanilla/Paper join message is not cancelled, replaced or modified. Recipients can therefore see both standard join presentation and NotifyLogin message.

## Vanish integration

Lookup is runtime/optional:

```java
FeatureServices.find(feature, VanishAPI.class)
```

- Vanish API available + vanished true: suppress announcement.
- API unavailable/feature disabled/failure represented as empty: treat player as not vanished and announce.

There is no proxy/global vanish lookup or delayed recheck. The decision occurs synchronously during join. If vanish state is restored after `PlayerJoinEvent`, a staff member can be announced before Vanish marks them hidden. Feature enable/order and Vanish state bootstrap must therefore be tested.

The implementation does not use `Player#canSee`, recipient-specific visibility or staff permission. If announced, even players who should not see the vanished staff identity receive the name.

## Audience and localization

Default key:

```text
notifylogin.supremeplus
```

Variables:

- `{name}` — raw current Bukkit player name string.

The message is built for each recipient, enabling normal per-audience language/localization. The joining player is already present in `Bukkit.getOnlinePlayers()` during join handling and generally receives their own announcement.

There is no explicit PlaceholderAPI preprocessing beyond shared localization behaviour and no feature PAPI expansion.

## Scope and ordering

The broadcast is backend-local only. Players connected to other Paper servers/proxy do not receive it.

At default `NORMAL` priority:

- Vanish or permissions may still be initialized/changed by later listeners;
- another plugin can suppress normal join message without affecting this one;
- there is no delay to wait for resource-pack, client world load or chat readiness;
- notification is sent immediately during join event processing.

No quit notification exists.

## Threading and performance

Join event runs on main server thread. Cost is one permission/API lookup plus localized message build/send for every local online player (O(n)). No task or asynchronous work is created.

## Persistence, database and messaging

None:

- no DataProvider/database;
- no Redis/proxy messaging;
- no watched-user persistence;
- no API registration;
- no PlaceholderAPI expansion.

The Supreme+ trigger is permission-state driven at join time.

## Lifecycle

Initialization constructs `NotificationHandler` then registers one listener. Disable is empty; feature lifecycle unregisters listener.

Enabling while players are already online does not announce them because there is no bootstrap loop.

## Developer source map

- Defaults/message/lifecycle: `features/notifylogin/NotifyLogin.java`
- Permission/vanish/broadcast: `features/notifylogin/internal/NotificationHandler.java`
- Join event: `features/notifylogin/listener/PlayerListener.java`
- Metadata: `features/notifylogin/meta/Meta.java`

## Operational verification

1. Join with/without exact Supreme+ permission.
2. Verify every local player, including joining player, receives message.
3. Verify players on other backends receive nothing.
4. Test Vanish enabled with state already restored before join notification.
5. Test Vanish disabled/unavailable and delayed vanish restore for possible privacy leak.
6. Test multiple recipient languages and `{name}`.
7. Compare with normal join message and other join plugins.
8. Enable feature while users are online and confirm no retroactive messages.

## Troubleshooting

- **Configured watched groups/delay do nothing:** those settings do not exist.
- **Everyone receives announcement:** intentional; there is no recipient permission/filter.
- **Vanished staff was announced:** Vanish API was unavailable or state was not restored before default-priority join handling.
- **Duplicate join messages:** NotifyLogin does not suppress vanilla/other plugin join messages.
- **Other servers receive nothing:** no Redis/proxy broadcast exists.
- **Nickname not shown:** `{name}` uses raw Bukkit account name; Nickname is not consulted.
