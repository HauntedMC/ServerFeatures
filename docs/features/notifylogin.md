# NotifyLogin

> Paper · Feature name `NotifyLogin` · feature package `features.notifylogin` · disabled by default

NotifyLogin is the authoritative local-server join and quit message feature. When enabled, it removes Paper's normal join and quit messages and optionally broadcasts a localized replacement selected for the connecting player.

The feature supports defaults for every player, ordered permission-based overrides, exact player overrides and vanish-safe delivery. It does not use Vault groups, a database of its own, Redis or proxy-wide broadcasts.

## Commands and permissions

No command is registered. Configuration changes are applied through the normal ServerFeatures reload lifecycle.

The default permission override uses:

```text
serverfeatures.feature.notifylogin.supremeplus
```

Additional permissions can be configured under `permission_overrides` without registering them in code.

## Default configuration

File: `plugins/ServerFeatures/features/NotifyLogin/config.yml`.

```yaml
enabled: false
announce_vanish_state_changes: true

default:
  join: notifylogin.default.join
  quit: notifylogin.default.quit

permission_overrides:
  supremeplus:
    priority: 100
    permission: serverfeatures.feature.notifylogin.supremeplus
    join: notifylogin.group.supremeplus.join
    quit: notifylogin.group.supremeplus.quit

player_overrides: {}
```

A message value is a localization key, not the rendered message itself.

- A missing `join` or `quit` value in a player or permission override inherits from the next matching layer.
- An explicitly empty value (`""`) suppresses that event and does not fall through.
- An empty or missing default value means no public message for that event.
- A present non-string message value is invalid, produces a startup warning and is suppressed rather than converted into a bogus key.
- An invalid priority produces a startup warning and uses `0`.
- `announce_vanish_state_changes` controls synthetic connection messages for explicit Vanish toggles and defaults to `true`.

## Selection precedence

Join and quit messages are resolved independently in this order:

1. exact UUID player override;
2. case-insensitive exact player-name override;
3. matching permission overrides ordered by descending `priority`;
4. the default message.

If multiple permission overrides use the same priority, their normalized identifiers are used as a deterministic tie-breaker and a startup warning is logged. Permission profile identifiers are case-insensitive; if the configuration contains duplicates that differ only by case, the last profile wins and a warning is logged.

Example:

```yaml
permission_overrides:
  staff:
    priority: 1000
    permission: serverfeatures.staff
    join: ""
    quit: ""

  supremeplus:
    priority: 100
    permission: serverfeatures.feature.notifylogin.supremeplus
    join: notifylogin.group.supremeplus.join
    quit: notifylogin.group.supremeplus.quit

player_overrides:
  "b5cfd842-5455-38a2-9c0d-da059d1e39e5":
    join: notifylogin.player.remymine.join
    quit: ""

  ExamplePlayer:
    join: notifylogin.player.example.join
```

UUID overrides are preferred because they survive player-name changes. Name entries remain useful for simple administrative configuration.

## Default messages

```yaml
notifylogin:
  default:
    join: "<color:#aab2c9>[<color:#ffd79c>+<color:#aab2c9>] {name}"
    quit: "<color:#aab2c9>[<color:#ffd79c>-<color:#aab2c9>] {name}"

  group:
    supremeplus:
      join: "<color:#aab2c9>[<color:#ffd79c>+<color:#aab2c9>]  <gradient:#3B8585:#3B8585:#2B9D9D:#2B9D9D:#43B1B1:#43B1B1:#44D6D6:#EAEAEA:#44D6D6>[Supreme+]</gradient> <color:#aab2c9>%serverfeatures_nickname%"
      quit: "<color:#aab2c9>[<color:#ffd79c>-<color:#aab2c9>]  <gradient:#3B8585:#3B8585:#2B9D9D:#2B9D9D:#43B1B1:#43B1B1:#44D6D6:#EAEAEA:#44D6D6>[Supreme+]</gradient> <color:#aab2c9>%serverfeatures_nickname%"
```

The legacy `notifylogin.supremeplus` key is migrated to `notifylogin.group.supremeplus.join`. A customized old value is preserved when the new destination still contains its generated default; the obsolete key is then removed. Existing customized destination values are never overwritten, and language-specific legacy values are migrated in their own files.

## Placeholders and localization

Explicit subject placeholders:

- `{name}` — Bukkit account name;
- `{display_name}` — Adventure display-name component;
- `{uuid}` — player UUID;
- `{profile}` — selected source such as `default`, `group:supremeplus` or `player:<identity>`.

Localization is selected separately for every recipient. PlaceholderAPI is evaluated against the joining or leaving player, not the recipient. This is required for values such as `%serverfeatures_nickname%` and prevents each recipient from seeing their own nickname in another player's announcement.

## Vanilla-message replacement

At `HIGHEST` event priority NotifyLogin always sets:

```java
event.joinMessage(null);
event.quitMessage(null);
```

This happens even when the selected custom value is empty. Enabling the feature therefore fully replaces Paper's standard local join and quit chat presentation.

The joining player receives their own join message. The quitting player is excluded only from a real disconnect quit broadcast. Synthetic join and quit messages caused by explicit Vanish toggles are also delivered to the affected staff member, so they see the same public-presence transition shown to other players. Recipients that cannot see the subject through Bukkit visibility are skipped. A rendering or delivery failure for one recipient is logged and does not prevent delivery to the remaining recipients.

## Vanish integration

When ServerFeatures Vanish is enabled, NotifyLogin waits for Vanish's canonical initial-state readiness result before broadcasting a join message. Vanish performs the persisted-state read once, applies that result on the main thread, and only then completes the readiness stage consumed by NotifyLogin. The normal message is already suppressed while this is pending.

- persisted or currently vanished: suppress the public join message;
- persisted visible and still visible: broadcast the resolved join message;
- lookup failure, scheduling failure or five-second timeout: fail closed, log the reason and suppress the message;
- player quits or reconnects before completion: discard the stale result.

NotifyLogin retains the result needed for a rapid quit. Vanish clears its runtime state at `MONITOR`, after NotifyLogin has evaluated the quit event. A player who is still vanished therefore never receives a public quit message. A player who later leaves vanish and becomes visible may receive the normal configured quit message.

With `announce_vanish_state_changes: true`, explicit Vanish changes are presented as connection changes using the same player, permission and default message resolution:

- entering vanish broadcasts the configured quit message to visible recipients and the affected staff member immediately before normal players lose Bukkit visibility of the staff member;
- leaving vanish restores Bukkit visibility first and then broadcasts the configured join message, including to the affected staff member;
- a transition while the initial join is still pending is fenced, so entering vanish cannot produce a leave for a player who was never announced;
- persisted vanish restoration during a real login and vanish restoration during feature reload remain silent and never produce a misleading extra leave;
- setting the option to `false` disables only these synthetic messages; normal join/quit replacement and vanish privacy tracking remain active.

When Vanish is unavailable at join time, the player is treated as visible. If a player was remembered as hidden and Vanish becomes unavailable before quit, NotifyLogin fails closed and suppresses that quit message rather than risking disclosure.

## Scope and lifecycle

Messages are backend-local. Moving between Paper backends can produce a quit message on the old backend and a join message on the new backend. Network-wide login/logout announcements require a separate proxy feature and are intentionally outside NotifyLogin.

Configuration is parsed and validated once during feature initialization. Pending asynchronous joins are fenced with per-session generations and cleared on disable, preventing stale results from a previous connection or reload from being announced.

## Operational verification

1. Enable NotifyLogin and confirm Paper's normal join and quit messages no longer appear.
2. Join without override permissions and verify the default `+` message.
3. Join with Supreme+ and verify the gradient prefix, spacing and subject nickname.
4. Configure two matching permissions and verify the higher priority wins.
5. Verify a missing event field falls through while `""` suppresses it.
6. Verify UUID overrides beat name and permission overrides.
7. Join and quit while persisted vanished; neither real connection message may be public.
8. Enter vanish while visible and verify one configured quit message appears for normal players and the vanishing player before Bukkit visibility is removed.
9. Leave vanish and verify one configured join message appears for normal players and the unvanishing player after Bukkit visibility is restored.
10. Set `announce_vanish_state_changes: false` and verify explicit vanish toggles no longer announce connection changes.
11. Test multiple recipient languages while the subject nickname remains correct.
12. Reload during a pending join and confirm no stale announcement is delivered.
13. Introduce an invalid message value, priority or vanish toggle setting and verify a clear warning plus safe fallback behavior.
