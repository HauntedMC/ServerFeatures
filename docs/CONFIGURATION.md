# Configuration Guide

This guide focuses on practical setup and safe operations. Keep changes small, test often, and roll out in steps.

## Configuration Layout

- Main settings: `plugins/ServerFeatures/config.yml` under `global.*`
- Feature settings: `plugins/ServerFeatures/features/<FeatureName>/config.yml`
- Framework messages: `plugins/ServerFeatures/lang/messages.yml` and optional language files
- Feature messages: `plugins/ServerFeatures/features/<FeatureName>/messages.yml` and optional language files
- Additional structured feature data: `plugins/ServerFeatures/local/*.yml`

Each feature file contains its own `enabled` toggle and settings. `config.yml` is reserved for values shared by
multiple features.

Feature reload behavior is file-scoped:

- `/serverfeatures reload <feature>` replaces that feature's runtime and preserves supported snapshot state
- `/serverfeatures softreload <feature>` refreshes only that feature's config and messages
- `/serverfeatures reloadlocal` refreshes framework messages
- `/serverfeatures reloadlocal <feature>` refreshes only that feature's messages

## Runtime Control Commands

Use these commands during operations:

- `/serverfeatures list`
- `/serverfeatures info <feature>`
- `/serverfeatures enable <feature>`
- `/serverfeatures disable <feature>`
- `/serverfeatures reload <feature>`
- `/serverfeatures softreload <feature>`
- `/serverfeatures reloadlocal`
- `/serverfeatures reloadlocal <feature>`

## Command Conflict Ownership

ServerFeatures commands can intentionally replace matching unnamespaced command labels and Brigadier roots registered by
another plugin. The global setting is enabled by default:

```yaml
global:
  commands:
    overwrite-conflicts: true
```

When enabled, command registration follows a reversible claim process:

- only the exact plain label, such as `/god`, is displaced;
- namespaced fallbacks such as `/essentials:god` remain registered;
- both Bukkit command-map entries and Brigadier roots are handled;
- all requested primary labels and aliases are claimed atomically;
- a failed registration restores every displaced binding;
- disabling or reloading the owning feature restores displaced bindings when their labels are still free;
- a newer command that appeared while the feature was active is never overwritten during restoration.

The setting applies only to conflicts with external command registrations. Labels already owned by another
ServerFeatures feature remain protected and are never silently replaced. Set `overwrite-conflicts` to `false` when the
server should retain strict fail-on-conflict behavior; a feature whose required command cannot register will then fail to
load rather than partially enabling.

The setting is read when a command is registered. Reload `config.yml` through the normal server configuration workflow
before enabling or reloading affected features.

## Recommended Workflow

1. Enable only the features you currently need.
2. Roll out one feature (or one related group) at a time.
3. Validate logs and in-game behavior.
4. Move to the next feature only after verification.

This keeps incidents small and rollback simple.

## Restart Operations

The Restart feature uses one generation-fenced lifecycle for manual, forced, scheduled, and automatic restarts.
Available commands are:

- `/restart`: start the configured warning countdown.
- `/restart force`: skip warnings, but still close joins, publish PREPARE, save, drain players, and shut down safely.
- `/restart schedule <date-or-day> <time>`: schedule a one-off restart.
- `/restart cancel`: cancel a scheduled restart, countdown, final delay, or PREPARE stage.
- `/restart status`: show the active phase, scheduled time, remaining countdown, or drain population.

Accepted schedule examples include `2026-08-01 05:00`, `01-08-2026 05:00`,
`2026-08-01T05:00`, `friday 05:00`, and `vrijdag 05:00`. A weekday/time that has already passed resolves to the
following week. `schedule.time_zone` accepts an IANA zone such as `Europe/Amsterdam`; `system` uses the server JVM zone.
Only one manual schedule or active restart may exist at a time.

The operational phases are `SCHEDULED -> COUNTDOWN -> FINAL_DELAY -> PREPARING -> DRAINING -> SHUTTING_DOWN`.
Cancellation is accepted through PREPARING. Once DRAINING starts, players may already have moved and cancellation is
therefore rejected. All callbacks carry a generation token, so a stale countdown, schedule monitor, PREPARE completion,
or drain task cannot affect a replacement restart.

### Controlled player drain

At PREPARING, the feature closes new backend joins before it snapshots players for autoreconnect. A Paper connection
validation listener rejects new logins, while a join listener provides a defensive fallback for connections that had
already passed validation when the gate closed.

The server runs `save-all flush`, then removes one player per configured interval. After the queue is exhausted, it waits
for an empty grace period and rechecks the live online set. Players who joined during the transition or whose first kick
did not complete are added to another staggered pass. Shutdown begins only after the server reports no players, except
for the bounded final fail-safe after `drain.max_wait_seconds`; that fail-safe also performs its final kick pass one player
at a time.

Key settings:

- `broadcast.use_chat` and `broadcast.use_titles`: control countdown presentation independently.
- `announce.schedule`: descending warning moments in seconds; zero is injected when omitted.
- `auto.enabled` and `auto.time`: recurring daily restart. The next day is scheduled before today's trigger runs, so a
  cancelled or skipped automatic restart cannot disable future daily restarts.
- `auto.wait_after_now_seconds`: delay after the zero-second announcement before PREPARING.
- `schedule.time_zone`: timezone used by manual schedules and the daily automatic restart.
- `schedule.check_interval_seconds`: polling interval for a one-off scheduled restart.
- `schedule.announce_hours_before`: hourly schedule notices before the target time.
- `drain.player_interval_millis`: delay between individual player kicks; default `150`.
- `drain.poll_interval_millis`: delay before processing a newly discovered straggler queue.
- `drain.empty_grace_millis`: settle time after a kick pass before checking whether the server is empty.
- `drain.max_wait_seconds`: bounded wait before the final staggered fail-safe pass.

## Restart Autoreconnect

Set `autoreconnect.enabled: true` in `features/Restart/config.yml` to coordinate controlled backend restarts with
ProxyFeatures over the durable Redis stream.

The restart flow persists a restart ID before shutdown, publishes `PREPARE`, waits briefly for proxy consumption, and
publishes `READY` only after Paper emits its full startup `ServerLoadEvent`. The marker is deleted only after `READY`
has been published successfully. Cancelling or reloading a prepared restart publishes `CANCEL` and removes the marker,
while normal plugin disable during the committed server shutdown deliberately preserves it for the next startup.
Cancellation is compare-by-restart-ID fenced: a late completion from an older restart cannot remove the marker of a
newer replacement restart.

ServerFeatures sends only the restarting backend identity and eligible player UUIDs. It does not choose a fallback server.
Velocity's existing routing remains authoritative; ProxyFeatures records the actual destination independently per player,
so a single restart may temporarily distribute players across multiple lobbies, limbo servers, or other backends.

Key settings:

- `autoreconnect.wait_after_ready_seconds`: extra warm-up time after full server startup before the first player returns.
- `autoreconnect.player_interval_millis`: delay between returning players, preventing a single-tick login spike.
- `autoreconnect.prepare_publish_timeout_millis`: maximum time to wait for PREPARE publication confirmation.
- `autoreconnect.prepare_settle_millis`: small post-publication grace period before draining players.
- `autoreconnect.session_ttl_seconds`: bounds stale restart sessions and markers.
- `autoreconnect.ready_publish_attempts` and `autoreconnect.ready_retry_seconds`: retry READY publication after startup.
- `autoreconnect.stream`: must match ProxyFeatures' `backend_autoreconnect.stream` setting.

If Redis or DataProvider is unavailable, the server restart still proceeds normally; only autoreconnect is skipped for
that cycle. The global `server_name` must exactly correspond to the backend name registered in Velocity.

## Environment-Specific Values

Treat production tokens, webhooks, and credentials as environment-specific values:

- keep secrets out of committed files;
- use your secret-management workflow;
- document expected variables for your team.

## Localization

Framework and feature messages are split intentionally.

- Put shared command/framework text in `lang/messages*.yml`.
- Put feature-owned text in `features/<FeatureName>/messages.yml` and optional language files.
- Use partial language files with only the entries you want to customize.
- Missing feature translations fall back to that feature's `messages.yml`, then to framework messages.

## Troubleshooting Tips

- If a feature does not enable, verify plugin dependencies and feature dependencies first.
- If a setting seems ignored, check path names and indentation.
- Apply one change at a time when diagnosing configuration behavior.
