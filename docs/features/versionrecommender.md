# VersionRecommender

> Paper · Feature ID `versionrecommender` · disabled by default · ViaVersion join advisory

VersionRecommender compares each joining player's ViaVersion protocol number with ViaVersion's highest native server protocol and sends one delayed advisory when the client is older or newer.

It does not define a manually configured recommended version/range and does not affect login compatibility. ViaVersion remains authoritative for protocol negotiation.

## Dependencies and activation

Initialization constructs `ViaVersionHook` around `Via.getAPI()`.

- When the API is unavailable, the feature logs a warning and registers no listener. The feature can remain nominally enabled but inactive.
- Availability is checked only during initialization and again at recommendation time.
- Enabling ViaVersion later does not register the listener until VersionRecommender is reconstructed.
- Direct ViaVersion API linkage must be compatible with the deployed version.

## Configuration

| Key | Default | Meaning |
|---|---:|---|
| `enabled` | `false` | Enables attempted ViaVersion integration. |
| `warn_players_older` | `true` | Warn when client protocol ID is numerically below native server protocol. |
| `warn_players_newer` | `true` | Warn when client protocol ID is numerically above native server protocol. |
| `delay_seconds` | `10` | Delay after join; clamped to zero or greater. |

Both warning booleans and delay are directly cast during service/listener construction. Wrong types can fail initialization. Values are captured once and do not change for the lifetime of those objects.

There is no permission, world filter, accepted-range list, per-player opt-out or repeat interval.

## Event and delayed flow

`PlayerJoinEvent` is handled at default `NORMAL` priority with no `ignoreCancelled` declaration.

For every join, the listener schedules one delayed task. At execution it first checks `player.isOnline()`, then calls the recommendation service.

The callback does not re-resolve the player/session by UUID or use a connection-generation token. A rapid reconnect around the delay depends on Bukkit player-object semantics and lifecycle task cancellation.

## Protocol comparison

At callback time:

1. require player online and Via API available;
2. read ViaVersion server's highest supported/native protocol ID;
3. map it through `ProtocolVersion.getProtocol(id).getName()`;
4. read player's ViaVersion protocol ID by UUID;
5. map client ID to display name;
6. skip all output only when server display name equals `UNKNOWN`;
7. client ID lower + older warnings enabled → send older message;
8. client ID higher + newer warnings enabled → send newer message;
9. equal IDs → no message.

Comparison is purely numeric. It does not inspect actual feature compatibility, ViaBackwards/ViaRewind support policy, protocol ranges or client mods.

Unknown client names are still inserted into messages because only the server name is checked for `UNKNOWN`.

Exceptions from ViaVersion/protocol mapping/localization are not caught by the listener/service. Task-manager logging and repetition semantics govern failures.

## Messages

| Key | Variables |
|---|---|
| `versionrecommender.warn-older` | `{version}` client protocol display name, `{server}` native server display name. |
| `versionrecommender.warn-newer` | same; default text also says the server will soon update. |

That update promise is static message copy, not derived from a release schedule or version API. Operators must edit it when inaccurate.

No PlaceholderAPI expansion is registered.

## Commands, permissions, persistence and messaging

None. The advisory is sent to every mismatched joining player allowed by the two booleans. There is no database, Redis, proxy message, acknowledgement or notification history.

A player reconnecting receives the advisory again after the delay.

## Lifecycle

`disable()` is empty. Listener/task cleanup relies on the lifecycle manager. The feature keeps no maps or caches.

The delayed callback checks online state but not feature enabled/generation state itself.

## Important implementation boundaries

- ViaVersion native protocol determines recommendation automatically.
- Feature silently becomes inactive when Via API is unavailable at initialization.
- Config is captured at construction.
- Numeric protocol order is treated as older/newer.
- Only server `UNKNOWN` suppresses output.
- No exception isolation exists in recommendation logic.
- One delayed task is created per join.
- No permission/opt-out/history exists.
- Newer warning's update promise is static text.
- The feature does not block, kick or change protocol negotiation.

## Verification checklist

1. Join with native, older and newer clients through ViaVersion.
2. Disable each warning flag independently.
3. Test zero/negative/wrong-type delay.
4. Remove ViaVersion before initialization and enable it afterward; confirm listener remains inactive.
5. Disable ViaVersion during the delay and inspect task failure behavior.
6. Test unknown/unregistered protocol IDs and display names.
7. Disconnect/reconnect before delayed callback.
8. Verify localized `{version}`/`{server}` names for every supported protocol.
9. Review newer-warning wording after every server update.

## Source map

- Defaults/dependency activation: `features/versionrecommender/VersionRecommender.java`
- Comparison/message logic: `features/versionrecommender/internal/RecommendationService.java`
- Join delay: `features/versionrecommender/listener/VersionRecommenderListener.java`
- ViaVersion wrapper/protocol names: `serverfeatures-api/.../api/hook/ViaVersionHook.java`
