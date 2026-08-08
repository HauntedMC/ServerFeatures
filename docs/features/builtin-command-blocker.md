# BuiltinCommandBlocker

`BuiltinCommandBlocker` hides and rejects built-in server commands for players without unregistering those commands from the server. Console senders, command blocks and other non-player command sources remain unaffected, so operational and scheduled use of Minecraft/Paper commands keeps working.

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

Third-party command namespaces are not blocked just because they exist. If a third-party plugin deliberately registers a command in a built-in namespace, that registration is treated as belonging to that built-in source.

## Allowlist

`allowed` contains command identifiers that should stay available to players. Entries are case-insensitive and may optionally start with `/`.

```yaml
allowed:
  - minecraft:gamemode
```

An allowlist match applies to the whole logical command. Its canonical name, namespaced registration and aliases remain available together; this avoids creating an accidental bypass in only one direction.

## Enforcement

The feature uses both layers intentionally:

1. blocked roots are removed from the command list sent to players, hiding them from normal client suggestions;
2. player command execution is checked server-side and cancelled before dispatch, so manually typing a hidden namespaced command does not bypass the blocker.

Commands are not unregistered from Paper's command map. This keeps console, command-block, scheduler and internal server usage intact.

The discovered command snapshot is refreshed when the feature starts, when the server finishes loading, when plugins enable or disable, and while Paper rebuilds a player's command list. Online players receive a command-tree refresh when the effective blocked set changes.

## Generated diagnostics

Everything below `generated` is informational output. It is never used as blocker configuration.

- `blocked_command_count` is the number of command registrations currently suppressed for players.
- `blocked_commands` is the alphabetically sorted set of registrations that were actually found and blocked.
- `detected_sources` shows how many blocked registrations were attributed to each source. `legacy_aliases` is an overlapping diagnostic count for blocked alias registrations.

Manual changes to `generated` have no effect. The feature replaces stale generated values with the currently discovered snapshot. The config file is only written when that generated snapshot actually changes.
