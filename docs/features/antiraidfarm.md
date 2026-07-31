# AntiRaidFarm

> Paper · Feature name `AntiRaidFarm` · feature package `features.antiraidfarm` · disabled by default

AntiRaidFarm rate-limits how often the same player may successfully trigger a raid on this Paper server. It listens to Paper/Bukkit's `RaidTriggerEvent`, allows the first non-bypassed trigger, starts a local in-memory cooldown, and cancels subsequent trigger attempts until that cooldown expires.

It does **not** inspect raid farms, village geometry, raid waves, mob spawning, worlds, claims, regions, or raid completion. The protection boundary is exactly the player-trigger event.

## Behaviour at a glance

- Cooldowns are keyed by player UUID.
- The cooldown begins when an uncancelled trigger reaches this listener and no active cooldown exists.
- A repeat trigger during the window is cancelled at `EventPriority.HIGHEST`.
- Staff with the bypass permission are ignored entirely and never receive or create a cooldown.
- Cooldowns live only in a Guava cache on the current backend; they are not persisted or synchronized.
- The administrator command is read-only and lists active cooldowns.

## Commands and permissions

| Syntax | Permission | Sender | Behaviour |
|---|---|---|---|
| `/antiraidfarm` | `serverfeatures.feature.antiraidfarm.command.admin` | Player or console | Equivalent to `list`; displays active cooldowns. |
| `/antiraidfarm list` | `serverfeatures.feature.antiraidfarm.command.admin` | Player or console | Displays active cooldowns sorted by remaining time descending. |

The command metadata declares the administrator permission and execution checks the same permission again before returning data. The only tab suggestion is `list`, and it is hidden from callers without the permission.

Unknown arguments return the literal usage line `/antiraidfarm list`; this usage output is not localized in the current implementation.

### Bypass permission

`serverfeatures.feature.antiraidfarm.bypass`

A bypassed player exits the event handler before any cache lookup or write. Consequently:

- their raid trigger is never cancelled by this feature;
- they never start a cooldown;
- a previously cached cooldown would also be ignored while they hold the bypass permission.

## Complete configuration reference

File: `plugins/ServerFeatures/features/AntiRaidFarm/config.yml`.

| Key | Default | Meaning and edge cases |
|---|---:|---|
| `enabled` | `false` | Enables the listener and administration command. |
| `raid_cooldown_seconds` | `600` | Cooldown after an allowed raid trigger. The handler clamps negative values to `0`. The value is read once during initialization; changing it requires a feature reload/re-enable. |
| `notify` | `true` | Sends `antiraidfarm.blocked` to a player whose repeat trigger is cancelled. Cancellation still occurs when this is false. Read once during initialization. |

### Zero-second cooldown

`raid_cooldown_seconds: 0` creates a cache configured with zero-second expiry. A trigger is marked, but the entry is immediately eligible for expiration and `remainingSeconds` calculates no positive remainder. Operationally this disables meaningful rate limiting while leaving the listener and command active.

### Reload behaviour

The cooldown duration and notification flag are copied into a new `AntiRaidFarmHandler` during feature initialization. Existing cache entries are not migrated across reload; the new handler starts empty.

## Event contract

### `RaidTriggerEvent`

Handler declaration:

```java
@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
```

Ordering implications:

1. Events cancelled by a lower-priority plugin are ignored and do not start a cooldown.
2. This feature runs late at `HIGHEST`, checks the player's current cooldown, and may cancel the event.
3. A `MONITOR` listener in another plugin can observe the final cancellation state.
4. Another `HIGHEST` listener has normal Bukkit same-priority ordering ambiguity; plugins should not rely on ordering within the same priority.

### Decision flow

For each event:

1. obtain `event.getPlayer()`;
2. return immediately for `serverfeatures.feature.antiraidfarm.bypass`;
3. capture `System.currentTimeMillis()`;
4. query the player's remaining cooldown;
5. when present, cancel the event and optionally send the blocked message;
6. otherwise, write the current epoch milliseconds to the cache as the new cooldown start.

The feature assumes that reaching the non-cancelled end of its `HIGHEST` handler represents a successful trigger for cooldown purposes. If a later `MONITOR` listener cannot cancel by contract, the cooldown remains valid. A non-standard plugin mutating raid state after the event does not roll back this cache entry.

## Cooldown calculation

The cache value is the epoch millisecond timestamp at which a trigger was allowed.

Remaining time is calculated as:

```text
elapsedMs = max(0, nowMillis - startedAtMillis)
remainingMs = cooldownSeconds * 1000 - elapsedMs
remainingSeconds = ceil(remainingMs / 1000)
```

- Negative clock movement is clamped by treating elapsed time as zero.
- Displayed seconds round upward, so a fraction of a second is shown as one full second.
- A non-positive remainder is treated as no cooldown even if Guava has not yet physically evicted the entry.

## Cache characteristics

`CacheBuilder` creates a local cache with `expireAfterWrite(cooldownSeconds, TimeUnit.SECONDS)`.

Important consequences:

- Accessing an entry does not extend it; only `markTriggered` writes a new timestamp.
- There is no explicit maximum size. The number of live entries is naturally bounded by unique triggering UUIDs within one cooldown period, but an abnormally high churn of unique UUIDs can temporarily grow the map.
- Guava performs expiration maintenance lazily/periodically as cache operations occur; every public read also independently checks the calculated remainder, so logically expired entries are never reported as active.
- Quit, raid completion, world unload, and player death do not clear a cooldown. Only expiry, feature replacement/reload, or process shutdown removes it.

## Administrator list output

`listActiveCooldowns()` snapshots `lastRaidCache.asMap()` and filters entries whose calculated remainder is positive.

For each active UUID:

1. `Bukkit.getOfflinePlayer(uuid)` is used to obtain the last known/cached name;
2. when no name is available, the UUID string is shown;
3. the record includes current remaining seconds and the configured total duration;
4. records are sorted from highest remaining time to lowest.

The command then emits:

| Message key | Variables |
|---|---|
| `antiraidfarm.list.none` | none |
| `antiraidfarm.list.header` | `{count}` active entries |
| `antiraidfarm.list.entry` | `{player}`, `{remaining}`, `{total}` |

Because name resolution uses Bukkit's offline-player API during command execution, administrators should avoid invoking the list in extremely large loops. The command itself performs no asynchronous handoff.

## Player notification

When a repeat trigger is blocked and `notify` is true, the player receives `antiraidfarm.blocked` with:

- `{seconds}` — rounded-up remaining seconds.

The message is sent after the event is cancelled. When notification is disabled, the gameplay result is unchanged and the trigger still fails silently.

## Persistence, database and messaging

AntiRaidFarm has no:

- DataProvider slot;
- database entity/table/repository;
- Redis publisher or subscription;
- proxy contract;
- PlaceholderAPI expansion;
- disk persistence.

Cooldowns are independent on every backend. A player can trigger a raid on server A and immediately trigger one on server B unless another network-level system enforces a shared policy. A backend restart or feature reload clears all cooldowns.

## Concurrency and thread expectations

`RaidTriggerEvent` and the command are expected on the Paper server thread. Guava's `Cache` is thread-safe, but Bukkit offline-player name resolution and event cancellation remain Bukkit operations and should stay on the server thread. The implementation creates no asynchronous tasks.

The list method iterates a weakly consistent concurrent cache view. An entry may expire or be added while the snapshot is being built; the command is diagnostic, not a transactional report.

## Lifecycle

Initialization:

1. read `raid_cooldown_seconds` and `notify` from the feature config;
2. construct a new handler and cache;
3. register `AntiRaidFarmListener`;
4. register `/antiraidfarm`.

Disable has no explicit method body. Framework lifecycle cleanup unregisters the listener/command, and the feature/handler/cache become unreachable. No task, subscription, database operation, or save must be drained.

## Developer source map

- Defaults, messages and lifecycle: `features/antiraidfarm/AntiRaidFarm.java`
- Event enforcement: `features/antiraidfarm/listener/AntiRaidFarmListener.java`
- Cache and calculation logic: `features/antiraidfarm/internal/AntiRaidFarmHandler.java`
- Administration command: `features/antiraidfarm/command/AntiRaidFarmCommand.java`
- Metadata: `features/antiraidfarm/meta/Meta.java`

## Operational verification

1. Enable the feature with a short cooldown such as 20 seconds.
2. Trigger a raid as a normal player and verify it is allowed.
3. Attempt another trigger immediately and verify `RaidTriggerEvent` is cancelled and the rounded remaining time is shown.
4. Set `notify: false`, reload, and verify cancellation remains but the message disappears.
5. Grant the bypass permission and verify triggers neither fail nor appear in the list.
6. Run `/antiraidfarm` and `/antiraidfarm list` from player and console; verify identical sorted output.
7. Remove the admin permission and verify execution and tab completion are denied.
8. Wait for expiry and verify the next trigger is allowed and starts a fresh full cooldown.
9. Have another plugin cancel a raid trigger before `HIGHEST`; verify no cooldown is created.
10. Restart/reload the feature and verify previous cooldowns are intentionally gone.
11. On a multi-backend setup, verify and document that cooldowns are server-local.

## Troubleshooting

- **Repeat raids are not blocked:** check `enabled`, `raid_cooldown_seconds > 0`, the bypass permission, and whether another plugin prevents the initial trigger before this listener sees it.
- **A blocked player receives no message:** check `notify` and the `antiraidfarm.blocked` localization key. Cancellation does not depend on message delivery.
- **The list shows a UUID:** Bukkit has no cached/known name for that UUID. This does not affect enforcement.
- **The displayed time is one second higher than expected:** remaining time is deliberately rounded upward.
- **Cooldown vanished after reload/restart:** expected; there is no persistence.
- **Players bypass by changing backend:** expected with the current local-only design. A network-wide requirement needs a shared database/Redis contract and atomic cooldown ownership.
- **A claim/region should be exempt:** no region integration exists. Add an explicit policy check in the event path rather than assuming WorldGuard or GriefPrevention is consulted.
