# Spawn

> Paper package `features.spawn` · metadata name `Spawn` · metadata version `1.0.0` · **not an implemented feature**

Spawn is currently a reserved package, not working server functionality. The only runtime class is an empty plain Java class:

```java
public class Spawn {
}
```

It does not extend `BukkitBaseFeature`, accept a `FeatureContext`, expose defaults/messages, or implement `initialize()`/`disable()`. Consequently it cannot participate in the normal ServerFeatures feature lifecycle in its current form.

## Actual operator surface

There is none.

| Surface | Current state |
|---|---|
| `/spawn` command | Not implemented or registered. |
| `/setspawn` or administration command | Not implemented. |
| Permission nodes | None. |
| Feature configuration | None. There is no `getDefaultConfig()`. |
| Localization messages | None. |
| Join/respawn/teleport listeners | None. |
| Scheduled tasks | None. |
| Database/DataRegistry state | None. |
| Redis/plugin messaging | None. |
| PlaceholderAPI | None. |
| Public API/service | None. |
| Reload/disable cleanup | None. |
| Tests | No Spawn behavior tests exist because there is no behavior. |

Creating a configuration section named `Spawn`, granting guessed permissions or expecting package discovery to register `/spawn` will not add functionality.

## Metadata

`features.spawn.meta.Meta` implements `BaseMeta` and reports:

- feature name: `Spawn`;
- feature version: `1.0.0`.

Metadata presence alone does not make the empty `Spawn` class a loadable feature. In the implemented modules, metadata accompanies a concrete feature class that extends the platform base and registers lifecycle resources.

## Events and ordering

Spawn listens to no Bukkit/Paper events. It does not alter:

- `PlayerJoinEvent`;
- `PlayerRespawnEvent`;
- `PlayerTeleportEvent`;
- death/bed/anchor spawn behavior;
- first-join positioning;
- world spawn locations;
- proxy server transfers.

Any current spawn behavior comes from Paper, world configuration or another plugin/feature.

## Persistence and messaging

There is no stored spawn location, per-player preference, database table, YAML store, Redis message or proxy contract. The package does not read Bukkit's world spawn or write it.

## What a complete implementation must define

Before this can be treated as a supported feature, its design needs explicit decisions rather than assumptions:

1. **Scope:** one backend-global spawn, per-world spawn, per-gamemode/network spawn, or multiple named spawns.
2. **Storage:** Bukkit world spawn, local feature YAML, DataRegistry/database, or a proxy-owned destination contract.
3. **Commands:** exact `/spawn`, `/setspawn`, named-target and staff-target syntax.
4. **Permissions:** use, bypass delay/cooldown, set/delete/list, other-player operations and console behavior.
5. **Teleport semantics:** immediate versus warm-up, movement/damage cancellation, safe-location checks, chunk loading and vehicle/passenger handling.
6. **Lifecycle events:** first join, every join, respawn, void rescue, world change and server transfer ordering.
7. **Cross-server behavior:** whether `/spawn` teleports locally or asks ProxyFeatures to connect to a designated backend.
8. **Failure behavior:** missing/unloaded world, unsafe location, database outage, player disconnect and feature reload during warm-up.
9. **Integration:** Vanish, AFK, Teleportation, Restart/autoreconnect, homes/warps, combat tagging and protection plugins.
10. **Observability/tests:** state migration, event priority, cancellation, concurrency and in-game verification.

A future implementation should extend the correct Paper feature base, use `FeatureContext<Meta>`, register every command/listener/task/service through the lifecycle manager, define deterministic disable/reload cleanup and replace this page with its real contract.

## Troubleshooting

If `/spawn` is unavailable, that is expected from this package. Check the plugin currently intended to own spawn teleportation instead of troubleshooting configuration or permissions here.

If the feature loader logs an issue involving Spawn, inspect whether the empty class was accidentally added to a discovery/registration list; it is not constructible as a normal `BukkitBaseFeature`.

## Source map

- Empty placeholder: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/spawn/Spawn.java`
- Metadata only: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/spawn/meta/Meta.java`
