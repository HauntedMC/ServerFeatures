# LagMonitor

> Paper · Feature name `LagMonitor` · feature package `features.lagmonitor` · disabled by default

LagMonitor asynchronously samples Paper's one-minute TPS value into a rolling in-memory history, periodically computes the arithmetic average of that history, notifies authorised local staff whenever the average is below a threshold, and rate-limits Discord webhook alerts.

It does not sample MSPT, tick durations, CPU, memory, GC, chunks, entities, network latency or timings/spark profiles. It does not persist metrics, expose commands/API/placeholders, automatically mitigate lag, or synchronize alerts through Redis/proxy messaging.

## Commands and permission

No command is registered.

Staff-notification permission:

```text
serverfeatures.feature.lagmonitor.notify
```

Every local online player with this permission receives a message on **every checker run** while the rolling average remains below threshold. The Discord cooldown does not rate-limit in-game staff messages.

## Complete configuration reference

File: `plugins/ServerFeatures/features/LagMonitor/config.yml`.

| Key | Default | Unit/exact behaviour and hazards |
|---|---:|---|
| `enabled` | `false` | Starts both asynchronous repeating tasks. |
| `tps_check_interval` | `5` | Seconds between TPS samples. Converted to task period `value * 20` ticks. Direct cast to `int` at construction. |
| `tps_monitor_duration` | `120` | Intended rolling-history duration in seconds. Capacity is integer division `duration / check_interval`. |
| `tps_alert_interval` | `600` | Minimum milliseconds-equivalent seconds between Discord alerts. Comparison is strict `>` rather than `>=`. |
| `tps_threshold` | `17.0` | Alert when calculated history average is strictly below this value. Direct cast to `double`. |
| `tps_checker_interval` | `60` | Seconds between average checks/local notifications. Converted to `value * 20` ticks. |
| `discordWebhookURL` | `https://discordhook.url` | Discord webhook used by async alert task. Empty/null warns and skips. Replace placeholder before production. |

Global setting:

| Key | Use |
|---|---|
| `server_name` | Inserted in local notification and Discord embed. Direct cast to `String`; null is passed through localization/JSON escaping behaviour. |

### Required numeric relationships and invalid values

No validation/clamping is performed.

- `tps_check_interval = 0` causes division by zero in history-capacity check and a zero task period.
- negative intervals/durations produce invalid task timing/capacity semantics.
- `tps_monitor_duration < tps_check_interval` makes capacity `0`; every sample sees size >= 0, calls `pollFirst()` on possibly empty deque, then adds one value. Effective history remains approximately one sample.
- durations not divisible by sample interval floor the sample capacity.
- a threshold above 20 can alert continuously under healthy operation; negative threshold normally never alerts.
- direct casts require exact/compatible runtime numeric types. YAML/config-service representation must provide `Integer` and `Double` values as expected.

Recommended:

```text
tps_check_interval > 0
tps_checker_interval > 0
tps_monitor_duration >= tps_check_interval
tps_alert_interval >= 0
0 <= tps_threshold <= 20
```

All settings are cached in final service fields at construction except webhook/server name, which are read when sending. Reload/re-enable for timing/threshold changes.

## Sampling source

Every logger run reads:

```java
Bukkit.getServer().getTPS()[0]
```

Index `0` is Paper's **one-minute TPS average**, not instantaneous TPS for the preceding `tps_check_interval`. LagMonitor then averages repeated overlapping one-minute averages over its history.

At defaults:

- sample every 5 seconds;
- keep up to `120 / 5 = 24` samples;
- each sample is already a one-minute Paper average;
- checker every 60 seconds computes mean of up to 24 overlapping one-minute averages.

This produces a smooth, delayed signal rather than a precise two-minute raw tick average. Brief spikes may be hidden and recovery/lag alerts can lag behind real conditions.

## Task scheduling and thread model

Two tasks start immediately (`initial delay 0`) using `scheduleAsyncRepeatingTask`.

### TPS logger

Period:

```text
tps_check_interval * 20 ticks
```

Flow:

1. call Paper TPS getter from async task;
2. lock `ReentrantLock`;
3. when deque size is at least `monitor_duration / check_interval`, remove oldest;
4. append sample;
5. unlock.

`ArrayDeque` access is protected by the lock.

### TPS checker

Period:

```text
tps_checker_interval * 20 ticks
```

Flow:

1. lock and average history;
2. empty history returns `20.0`;
3. when average < threshold, format to two decimals;
4. notify local staff;
5. compare wall-clock Discord cooldown;
6. when elapsed > configured alert interval, schedule another async task for webhook and immediately update `lastDiscordAlert`.

Because both tasks start at delay zero, logger/checker relative ordering is scheduler-dependent. The first checker can see empty history and treat it as 20 TPS.

### Async Bukkit access caveat

The checker async task calls:

- `Bukkit.getOnlinePlayers()`;
- player permission checks;
- Adventure `sendMessage`;
- localization building.

These are performed without main-thread handoff. Paper may tolerate some audience operations, but Bukkit player collection/access is generally expected on the server thread. A safer design would calculate asynchronously and schedule notification iteration synchronously.

The TPS logger also calls Paper server TPS from async context; verify target API thread-safety.

## History/capacity semantics

History capacity is re-evaluated from final cached values on every sample:

```text
capacity = MONITOR_DURATION / CHECK_INTERVAL
```

When size >= capacity, exactly one oldest value is removed before adding. For valid positive capacity this keeps size at or below capacity.

There is no timestamp per sample. The system assumes scheduler cadence. Delayed/skipped task executions do not change retention by elapsed time; history is count-based.

The average is a simple unweighted arithmetic mean. Old/new samples have equal weight.

No NaN/infinite filtering is applied. If Paper returns a non-finite value, it enters history and can make comparisons/formatting behave unexpectedly.

## Alert semantics

### Local staff alert

Every below-threshold checker invocation iterates local players with notify permission and sends:

```text
lagmonitor.notify_lag
```

Variables:

- `{server}` — global server name;
- `{tps}` — `String.format("%.2f", avgTps)`.

`String.format` uses JVM default locale. In locales using comma decimal separator, `{tps}` may be `16,50` rather than `16.50`.

There is no recovery notification, per-player cooldown, acknowledgement, severity tier or state transition detection. At default checker interval, staff are notified once per minute for the entire incident.

### Discord alert rate limiting

`lastDiscordAlert` starts at zero. First below-threshold check normally sends immediately because current epoch minus zero exceeds interval.

Subsequent alert when:

```text
nowMillis - lastDiscordAlert > tps_alert_interval * 1000L
```

The timestamp is updated immediately after scheduling the webhook task, not after successful HTTP delivery. A failed webhook still consumes the cooldown.

Negative alert interval makes every below-threshold checker eligible. Integer multiplication is promoted to long after `ALERT_INTERVAL`, but the expression uses `ALERT_INTERVAL * 1000L`, avoiding int overflow.

There is no synchronization/volatile on `lastDiscordAlert`; only the single checker task normally accesses it.

## Discord payload

Orange embed (`16753920`) with fixed Dutch title/description:

- server name;
- formatted average TPS;
- current ISO-8601 timestamp;
- HauntedMC author/footer/icon;
- feature version.

Server name is JSON-escaped; formatted TPS is inserted directly but consists of formatter output. Transport uses `DiscordUtils.sendPayload`.

No retry, HTTP response metric, queue, timeout config, rate-limit backoff, database fallback or recovery webhook exists.

## Messages

| Key | Variables | Use |
|---|---|---|
| `lagmonitor.notify_lag` | `{server}`, `{tps}` | Local authorised-player alert. |

There are no user-facing Discord success/failure messages.

## What the metric does and does not prove

A below-threshold rolling average indicates Paper's smoothed one-minute TPS values have remained low enough to pull the history mean below threshold. It does not identify cause.

Use spark/timings, GC logs and host/container metrics to investigate:

- long synchronous plugin tasks;
- entity/chunk load;
- database/network blocking;
- GC/heap pressure;
- CPU steal/thermal throttling;
- disk stalls;
- world generation.

LagMonitor performs no automatic entity removal, chunk unload, restart, command execution or player kick.

## Persistence, database and messaging

None. History and Discord cooldown are in memory only and reset on feature/server restart. No DataProvider, Redis, proxy message, API or PlaceholderAPI expansion exists.

Every backend monitors itself independently. Staff on another server receive nothing unless they are connected locally or Discord is configured.

## Lifecycle and shutdown

Initialization constructs services and immediately starts both lifecycle-owned async repeating tasks.

`disable()` is empty. Correct cleanup depends entirely on feature task lifecycle cancellation. History/lock/service references become unreachable; no explicit final alert, task handle cancellation or in-flight Discord drain exists.

An already-scheduled webhook async task may be governed by lifecycle cancellation/admission semantics.

## Developer source map

- Defaults/lifecycle: `features/lagmonitor/LagMonitor.java`
- Composition: `features/lagmonitor/internal/LagMonitorHandler.java`
- Sampling/history/alerts: `features/lagmonitor/internal/service/TPSMonitorService.java`
- Discord payload: `features/lagmonitor/internal/service/DiscordService.java`
- Metadata: `features/lagmonitor/meta/Meta.java`

## Operational verification

1. Validate every numeric setting, especially zero/negative/division cases, on a test server.
2. Compare sampled value with Paper `/tps` and understand it is the one-minute average.
3. Force a controlled TPS drop and measure detection/recovery delay.
4. Verify local messages repeat every checker interval and only for notify permission.
5. Verify Discord first alert/cooldown and failure consuming cooldown.
6. Test decimal formatting under production JVM locale.
7. Test server name null/special JSON characters.
8. Enable Paper async-catcher/thread diagnostics and inspect online-player/message calls.
9. Disable/reload during tasks/webhook and verify no callbacks survive feature scope.
10. Correlate alerts with spark/timings/host data; confirm no mitigation action occurs.

## Troubleshooting

- **Alerts are delayed:** the source is already a one-minute TPS average and then averaged over history.
- **Staff get repeated messages:** local notifications have no cooldown/state-transition logic.
- **Discord did not retry:** failed scheduled delivery still updates cooldown and no retry exists.
- **No alert at startup despite lag:** first checker may run before first sample and treats empty history as 20.
- **Arithmetic/division errors:** ensure positive check interval and sensible monitor duration.
- **Async-thread warnings:** player iteration/messaging occurs in async checker.
- **Config changes have no effect:** timings/threshold are cached until feature reconstruction.
- **MSPT/CPU/memory are missing:** not implemented; only `getTPS()[0]` is sampled.
