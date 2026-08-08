# BuiltinCommandBlocker

`BuiltinCommandBlocker` suppresses built-in server commands discovered from Paper's live command registry. By default it only makes those commands unavailable to players: the commands remain registered for console senders, command blocks, schedulers and other non-player command sources.

The feature is opt-in like other ServerFeatures features. Once enabled, every built-in source is blocked by default.

```yaml
enabled: false

block:
  minecraft: true
  bukkit: true
  paper: true
  spigot: true
  spark: true
  legacy_aliases: true

# false: keep commands registered and only make them unavailable to players.
# true: remove blocked registrations from Paper's live command map entirely.
remove_from_command_map: false

allowed: []

generated:
  blocked_command_count: 0
  blocked_commands: []
  detected_sources:
    minecraft: 0
    bukkit: 0
    paper: 0
    spigot: 0
    spark: 0
    legacy_aliases: 0
```

## Sources

The blocker discovers the server's current command registrations instead of relying on a static command-name list. Commands are classified from their registered namespace, Bukkit command implementation and, where applicable, the owning plugin.

- `minecraft` covers vanilla/Mojang commands and their `minecraft:` registrations.
- `bukkit` covers Bukkit default commands.
- `paper` covers Paper-owned commands.
- `spigot` covers Spigot-owned compatibility commands.
- `spark` covers the Spark profiler bundled with Paper or installed as Spark.
- `legacy_aliases` controls aliases that point at an otherwise blocked built-in command.

Modern plugin commands may be represented internally by Paper wrapper classes. Plugin ownership is checked before Paper implementation-package heuristics so ordinary ServerFeatures and third-party commands are not mistaken for Paper built-ins. Third-party namespaces are not blocked just because they exist. If a third-party plugin deliberately registers a command in a built-in namespace, that registration is treated as belonging to that built-in source.

## Allowlist

`allowed` contains command identifiers that should stay available. Entries are case-insensitive and may optionally start with `/`.

```yaml
allowed:
  - minecraft:gamemode
```

An allowlist match applies to the whole logical command. Its canonical name, namespaced registration and aliases remain available together; this avoids creating an accidental bypass in only one direction.

## Enforcement

With the default `remove_from_command_map: false`, the feature uses two player-facing layers:

1. blocked roots are removed from the command list sent to players, hiding them from normal client suggestions;
2. player command execution is checked server-side and cancelled before dispatch, so manually typing a hidden namespaced command does not bypass the blocker.

The commands themselves stay registered in this mode. Console, command-block, scheduler and internal server usage therefore keeps working.

With `remove_from_command_map: true`, every blocked registration is removed from Paper's live command map/Brigadier root. This is global: players, console, command blocks and other senders can no longer execute those removed registrations. ServerFeatures remembers the exact registrations it removes and restores them when hard removal is turned off or when the feature is disabled/reloaded. A command that has been replaced by another registration while it was removed is never overwritten during restoration.

The discovered command snapshot is refreshed when the feature starts, when the server finishes loading, when plugins enable or disable, and while Paper rebuilds a player's command list. Online players receive a command-tree refresh when the effective blocked set or hard-removal state changes.

## Generated diagnostics

Everything below `generated` is informational output. It is never used as blocker configuration.

- `blocked_command_count` is the number of command registrations currently blocked by policy, including registrations currently removed in hard-removal mode.
- `blocked_commands` is the alphabetically sorted set of registrations that were actually found and blocked.
- `detected_sources` shows how many blocked registrations were attributed to each source. `legacy_aliases` is an overlapping diagnostic count for blocked alias registrations.

Manual changes to `generated` have no effect. The feature replaces stale generated values with the currently discovered snapshot. The config file is only written when that generated snapshot actually changes. A failure to save these diagnostics does not disable command enforcement.
