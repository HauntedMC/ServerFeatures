# CommandScheduler

> Paper · Feature ID `CommandScheduler` · disabled by default · commands `/commandscheduler`, `/cmdscheduler`

CommandScheduler executes recurring console commands at configured wall-clock times. Schedules are stored separately from feature settings in `plugins/ServerFeatures/local/commandscheduler.yml` and can be created, edited, tested, enabled, disabled and removed in game.

The feature intentionally supports daily and weekly schedules rather than exposing cron expressions. A trigger can either execute every configured command in order or select exactly one random command.

## Storage layout

Feature settings and schedule data are separated:

- `features/CommandScheduler/config.yml` contains time-zone, command-delay and logging settings;
- `features/CommandScheduler/messages.yml` contains localized command feedback;
- `local/commandscheduler.yml` contains administrator-managed schedules.

The local file is created automatically with an empty `schedules` map. In-game changes are written through the shared atomic YAML persistence layer before the runtime snapshot is replaced. If saving fails, the currently active schedule set remains unchanged. If the root `schedules` value is not a map, the feature loads no schedules and blocks in-game mutations until an operator corrects and reloads the file; it never overwrites that malformed root automatically.

## Schedule format

```yaml
schedules:
  daily_announcement:
    enabled: true
    trigger:
      type: daily
      time: "20:00"
    mode: sequence
    commands:
      - "broadcast chat De dagelijkse beloningen zijn beschikbaar!"
      - "rewards distribute daily"

  friday_event:
    enabled: true
    trigger:
      type: weekly
      day: FRIDAY
      time: "21:00"
    mode: random
    commands:
      - "event start spleef"
      - "event start parkour"
      - "event start trivia"
```

Schedule IDs are case-insensitive and stored in lower case. They may contain `a-z`, `0-9`, `_` and `-`.

Times use strict `HH:mm` format. Weekly days accept canonical English names and common English or Dutch aliases, including `monday`, `mon`, `maandag` and `ma` through `sunday`, `sun`, `zondag` and `zo`.

Commands are stored without a leading slash. A leading slash supplied through YAML or the command interface is removed automatically. Duplicate commands are allowed because repeated execution can be intentional.

A schedule created in game starts disabled. An enabled schedule must contain at least one command.

## Execution modes

### Sequence

`mode: sequence` executes every command in list order. The first command is dispatched immediately and later commands are spaced by `command-delay-ticks`. A rejected or failing command is logged and does not stop the remaining sequence.

### Random

`mode: random` selects one configured command uniformly for each occurrence. Consecutive occurrences may select the same command. Selection history is not persisted.

Schedules due at the same instant are ordered by schedule ID and submitted to one serial execution queue. Their command chains therefore do not interleave.

All dispatch uses the Bukkit console sender on the primary server thread. Commands are never dispatched asynchronously.

## Commands and permissions

The base permission grants every subcommand. Granular permissions can be granted independently.

| Syntax | Permission | Behaviour |
|---|---|---|
| `/commandscheduler list [page]` | `serverfeatures.feature.commandscheduler.command.commandscheduler.view` | Lists schedules, state, trigger, mode, command count and next run. |
| `/commandscheduler info <id>` | view | Shows full schedule information. |
| `/commandscheduler create <id> daily <HH:mm> <sequence\|random>` | `...manage` | Creates a disabled daily schedule. |
| `/commandscheduler create <id> weekly <day> <HH:mm> <sequence\|random>` | manage | Creates a disabled weekly schedule. |
| `/commandscheduler delete <id>` | manage | Permanently removes a schedule. |
| `/commandscheduler enable <id>` | manage | Enables a schedule when it contains commands. |
| `/commandscheduler disable <id>` | manage | Disables a schedule without deleting it. |
| `/commandscheduler set schedule <id> daily <HH:mm>` | manage | Replaces the trigger with a daily trigger. |
| `/commandscheduler set schedule <id> weekly <day> <HH:mm>` | manage | Replaces the trigger with a weekly trigger. |
| `/commandscheduler set mode <id> <sequence\|random>` | manage | Changes execution mode. |
| `/commandscheduler command list <id>` | view | Shows the indexed command list. |
| `/commandscheduler command add <id> <command...>` | manage | Appends a command. |
| `/commandscheduler command set <id> <index> <command...>` | manage | Replaces one command by one-based index. |
| `/commandscheduler command remove <id> <index>` | manage | Removes one command. The last command cannot be removed while enabled. |
| `/commandscheduler run <id>` | `...run` | Queues one manual test execution without changing the automatic trigger. Disabled schedules may be tested. |
| `/commandscheduler reload` | `...reload` | Reloads feature settings and local schedule data, validates entries and recalculates the next trigger. |

Full permission nodes:

- `serverfeatures.feature.commandscheduler.command.commandscheduler`
- `serverfeatures.feature.commandscheduler.command.commandscheduler.view`
- `serverfeatures.feature.commandscheduler.command.commandscheduler.manage`
- `serverfeatures.feature.commandscheduler.command.commandscheduler.run`
- `serverfeatures.feature.commandscheduler.command.commandscheduler.reload`

A command currently being dispatched by CommandScheduler may not invoke `/commandscheduler run` before that dispatch returns. This prevents synchronous direct or indirect execution loops.

## Configuration

```yaml
enabled: false
time-zone: system
command-delay-ticks: 2
logging:
  log-executions: true
  log-commands: false
```

| Key | Default | Meaning |
|---|---:|---|
| `enabled` | `false` | Enables the feature and registers its command interface. |
| `time-zone` | `system` | Wall-clock zone. Use `system` or an IANA zone such as `Europe/Amsterdam`. Invalid values fall back to the JVM system zone with a warning. |
| `command-delay-ticks` | `2` | Delay between queued commands. Negative values clamp to zero. |
| `logging.log-executions` | `true` | Logs schedule batches queued for automatic or manual execution. |
| `logging.log-commands` | `false` | Logs full command text before dispatch. Leave disabled when commands may contain sensitive arguments. |

## Scheduler lifecycle

The coordinator keeps one lifecycle-scoped Bukkit task for the earliest next occurrence. Every create, edit, delete, enable, disable or reload operation invalidates the old callback with a generation token and recalculates the earliest trigger.

When the callback runs:

1. it verifies that its generation is still current;
2. it checks the wall clock and reschedules the remaining delay if Bukkit invoked it early due to tick rounding;
3. it collects every schedule due at the same instant;
4. it schedules the next recurrence before submitting the current batch;
5. it sends the due schedules to the serial command executor.

Feature disable cancels the pending trigger, clears queued command work and relies on the feature lifecycle manager to cancel all remaining delayed tasks.

## Time-zone and downtime semantics

- Daily triggers select the next future local occurrence.
- Weekly triggers select the next matching weekday and time.
- A time exactly equal to the current instant is considered elapsed and resolves to the next recurrence.
- Missed occurrences during server or feature downtime are not replayed.
- Reloading after a trigger time does not produce a catch-up burst.
- A nonexistent local time during the spring DST gap is skipped for that recurrence.
- A repeated local time during the autumn DST overlap runs once at the earlier offset.
- A late Bukkit callback still executes its occurrence once, provided it was not invalidated by a newer schedule generation.

## Validation and failure handling

Each schedule is parsed into an immutable validated runtime model. Invalid entries are logged with their schedule ID and skipped without disabling valid schedules. Existing unrelated invalid raw entries are retained when valid schedules are changed in game, preventing a management action from erasing operator data. An invalid case-variant alias is removed when its canonical schedule is created or deleted so malformed data cannot later resurrect a deleted schedule.

Validation rejects:

- invalid or duplicate IDs;
- a non-boolean `enabled` value;
- malformed trigger maps;
- unsupported trigger types or modes;
- invalid days or non-strict times;
- non-list command sections or non-string command entries;
- enabled schedules without commands;
- blank commands.

Unknown console commands return a warning but do not abort a sequence. Exceptions from one command are logged with their full stack trace, isolated, and later queued commands continue.

## Operational verification

1. Create a disabled daily schedule, add two commands, enable it and verify `list`/`info` show the next run.
2. Run the schedule manually and verify exact ordering with the configured tick delay.
3. Change it to random mode and verify exactly one command executes per run.
4. Create two schedules for the same time and verify schedule-ID ordering without command interleaving.
5. Disable, edit, reload and delete schedules while a future callback exists; verify stale callbacks do not execute.
6. Test an invalid YAML entry beside a valid one and verify the valid schedule remains active.
7. Test `Europe/Amsterdam` around both DST transitions.
8. Disable or reload the feature with commands queued and verify no delayed commands survive cleanup.
