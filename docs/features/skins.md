# Skins

> Paper · Feature ID `skins` · disabled by default · command `/skin`

Skins temporarily replaces the signed `textures` property in an online Paper player's profile with the official textures of another Minecraft account. It performs direct Mojang API lookups asynchronously, applies the resulting signed profile property on the main thread, tracks a small amount of local state and fires `SkinUpdateEvent` so other visual features—most notably Nametags—can reconcile after the player profile changes.

The feature does not persist custom skin selections. State is cleared on quit and disable, and there is no database, Redis message or proxy synchronization.

## Commands and permissions

| Syntax | Permission | Sender | Behaviour |
|---|---|---|---|
| `/skin <name>` | `serverfeatures.feature.skins.command.skin.self` | Player only | Applies the official skin of another account to the sender, subject to self cooldown. |
| `/skin remove` | same | Player only | Attempts to restore the sender's own official textures. No cooldown. |
| `/skin <online-player> <name>` | `serverfeatures.feature.skins.command.skin.others` | Any command sender | Applies a donor skin to an exact online target. No cooldown. |
| `/skin <online-player> remove` | same | Any command sender | Attempts to restore the target's own official textures. No cooldown. |

The command has no aliases or command-meta permission. Permission checks are performed in `execute()`.

Console and command blocks cannot use the one-argument form; they receive the staff-form usage message. Target resolution uses `Bukkit.getPlayerExact`, so partial names and offline players are rejected.

Tab completion:

- first argument always suggests `remove`;
- with others permission, first argument also suggests online player names;
- second argument for staff suggests only `remove`;
- arbitrary donor account names cannot be suggested;
- results are prefix-filtered and first-argument output is limited to 20.

## Permissions

| Node | Effect |
|---|---|
| `serverfeatures.feature.skins.command.skin.self` | Self-service apply/remove. |
| `serverfeatures.feature.skins.command.skin.others` | Staff apply/remove for exact online targets. |
| `serverfeatures.feature.skins.bypass.cooldown` | Bypasses only the self-apply cooldown. Staff-target operations already bypass it by design. |

There is no separate remove permission, lookup permission, offline-target permission or skin-name allow/deny permission.

## Configuration

| Key | Default | Meaning |
|---|---:|---|
| `enabled` | `false` | Registers the command/listener and permits skin operations. |
| `cooldown_seconds` | `60` | Cooldown reserved when a self-service apply lookup begins. Values `<=0` disable the cooldown. Non-numeric values fall back to `60`. |

The cooldown value is read dynamically through `SkinState#getCooldownSeconds`, so a config reload visible through the config handler can affect later checks without reconstructing the state object.

The feature has no configuration for:

- Mojang endpoints;
- request/connect timeout;
- retry count/backoff;
- cache TTL;
- persistence;
- allowed/blocked donor names;
- staff cooldown;
- profile refresh strategy.

Those are hard-coded.

## Name validation

Donor input is trimmed and matched against the shared Minecraft-name pattern `TextPatterns.MC_NAME`. The user-facing message defines the accepted contract as:

- 3–16 characters;
- letters;
- digits;
- underscore.

Validation happens before cooldown reservation or HTTP work. A syntactically valid but nonexistent name proceeds to Mojang and eventually produces `skins.lookup_failed`.

## Self cooldown semantics

Cooldown state is a concurrent map `UUID -> last-use epoch milliseconds`.

For self **apply** only:

1. bypass permission skips the check entirely;
2. configured seconds `<=0` means no cooldown;
3. elapsed time is calculated using truncated whole seconds;
4. an active cooldown sends `{seconds}` and does no lookup;
5. an allowed attempt immediately writes the current timestamp before HTTP work begins.

The reservation is cleared when:

- donor profile lookup fails;
- the async operation throws;
- the target goes offline before main-thread application;
- the player quits.

It remains after successful application. Removing a skin never checks or starts cooldown. Staff operations (`isSelf=false`) never use cooldown.

Because state is local and cleared on quit, reconnecting resets the cooldown.

## Apply workflow

`SkinService.applySkin(actor, target, donorName, isSelf)` follows this sequence:

1. Trim and validate donor name.
2. Reserve/check self cooldown where applicable.
3. Schedule the `skins.working` message on the main task manager.
4. Log actor, donor input and target.
5. Schedule asynchronous Mojang lookup.
6. Resolve donor name to UUID.
7. Resolve UUID to a signed profile with `?unsigned=false`.
8. Require a signed `textures` property containing both value and signature.
9. Schedule main-thread completion.
10. Re-resolve target by UUID through `Bukkit.getPlayer` and require online state.
11. Read the target's current `PlayerProfile`.
12. Remove every property named `textures`.
13. Add the signed donor property.
14. Call `Player#setPlayerProfile`.
15. Mark local custom-skin state true.
16. Fire `SkinUpdateEvent(player, SET, donorCanonicalName)`.
17. Send self/staff/target success messages.

The target UUID/profile identity is not replaced; only the signed textures property changes.

The feature assumes `setPlayerProfile` performs the necessary Paper client-view refresh. It does not manually hide/show players, respawn entities, send packets or relog the player.

## Remove workflow

Removal first checks the local `hasCustomSkin` map. If false, it sends the relevant `none_applied` message and performs no Mojang lookup.

When true:

1. optionally send `skins.removing` for self-service;
2. asynchronously fetch the target UUID's official signed profile from Session Server;
3. schedule main-thread completion;
4. if target is offline, clear local custom state and stop;
5. if official textures exist, replace the profile textures and fire `SkinUpdateEvent(player, REMOVE, officialCanonicalName)`;
6. if official textures are unavailable, log a warning but continue;
7. clear local custom state;
8. send removed-success messages.

Important consequence: removal reports success and clears `hasCustomSkin` even when the official textures could not be retrieved/applied. In the exception path, an online target is likewise marked not custom and receives success messaging. A later `/skin remove` then reports that no custom skin is applied, even if the donor textures remain visible.

There is no fallback that reconstructs the original profile from the login session or forces a relog.

## Mojang HTTP contract

Hard-coded endpoints:

```text
GET https://api.mojang.com/users/profiles/minecraft/<name>
GET https://sessionserver.mojang.com/session/minecraft/profile/<undashed-uuid>?unsigned=false
```

HTTP client/request settings:

| Setting | Value |
|---|---:|
| Connect timeout | 5 seconds |
| Per-request timeout | 8 seconds |
| Base retry backoff | 250 ms × exponential factor + 100–399 ms jitter |
| `Retry-After` | Numeric seconds supported; minimum 1 second |
| User-Agent | plugin name/version plus HauntedMC URL/contact text |

Status handling:

- `200`: parse response;
- `204` or `404`: return no data without retry;
- `429`: retry using `Retry-After`/backoff;
- `5xx`: retry with backoff;
- other status: return failure to caller;
- network exceptions: retry, then log and return null;
- interruption: restore interrupt flag and return null.

Although the source comment says two retries/three total requests, the loop condition permits continuation while `attempt <= MAX_RETRIES + 1`; with `MAX_RETRIES=2`, some 429/5xx/exception paths can reach a fourth request. Operational rate-limit calculations should follow the code, not the comment.

The implementation uses blocking `HttpClient#send` and `Thread.sleep`, but both run inside the feature's async task.

## JSON/profile requirements

Name lookup requires an `id` field containing a 32-character hexadecimal undashed UUID.

Session profile lookup:

- uses response `name` as canonical display name, falling back to input name;
- scans `properties` for the first `name == "textures"` entry;
- requires both `value` and `signature`;
- creates Paper's signed `ProfileProperty`;
- rejects unsigned/missing textures.

Malformed JSON or UUID data is logged and treated as lookup failure.

## TTL caches

One `SkinService` instance is constructed with the registered command and owns two concurrent maps:

| Cache | TTL | Key/value |
|---|---:|---|
| name → UUID | 5 minutes | Input donor string → resolved UUID |
| UUID → profile | 2 minutes | UUID → canonical name + signed textures property |

Only successful non-null values are cached. Failures are not negative-cached.

Name keys are not normalized to lower case, so `Notch`, `notch` and other case variants can occupy separate entries. Expired entries are replaced on access but never proactively removed; the maps can retain expired keys until feature/command object disposal.

The UUID profile cache is also used when restoring a player's own skin. A profile fetched within the prior two minutes may therefore be reused rather than contacting Session Server again.

## In-memory state

`SkinState` stores:

- `lastUse`: self cooldown timestamps;
- `hasCustomSkin`: boolean presence markers.

It does **not** store:

- donor name;
- signed texture value/signature;
- original player texture;
- operation generation/in-flight request;
- actor identity;
- persistence version.

`PlayerQuitEvent` at default `NORMAL` priority clears both maps for that UUID. Feature disable clears all state.

The profile itself can remain changed until the player disconnects or another profile operation replaces it, but the feature intentionally forgets ownership on quit. On the next login it assumes no custom skin.

## `SkinUpdateEvent`

After a successful profile replacement the feature synchronously fires a custom Bukkit event on the main thread.

Fields:

| Field | Meaning |
|---|---|
| `player` | Online player whose profile was changed. |
| `type` | `SET` or `REMOVE`. |
| `newSkinName` | Donor canonical name for SET; player's official canonical name for REMOVE. |

The event is not cancellable and is fired **after** `setPlayerProfile` and state mutation. A listener cannot veto the update.

Nametags listens for this event to rebuild/reconcile its viewer-specific representation. Other consumers can use it as a post-commit notification.

No event is fired when removal fails to fetch official textures, even though local state and success messages may still say removal completed.

## Threading and completion fencing

HTTP and JSON work are asynchronous. Bukkit profile mutation, online revalidation, messages and event firing are scheduled back onto the task manager's main-thread path.

The target is re-resolved by UUID before apply/remove completion. However:

- actor online state is not revalidated before sending completion messages;
- there is no per-player operation token, so two concurrent staff/self requests can complete out of order and the last HTTP completion wins;
- there is no explicit feature generation/disabled check inside completion callbacks;
- correctness during disable relies on lifecycle-manager cancellation/rejection of feature tasks;
- state can be cleared by quit while an async task is still running, though apply completion refuses an offline target.

Concurrent lookups for the same uncached key are not coalesced; each can call Mojang and race to populate the cache.

## Messages and variables

| Message | Variables / condition |
|---|---|
| `skins.usage.self` | Invalid self command shape. |
| `skins.usage.other` | Invalid staff/console shape. |
| `skins.applied.self` | `{skin}` canonical Mojang name. |
| `skins.removed.self` | Successful/logically completed self removal. |
| `skins.none_applied.self` | Local state has no custom marker. |
| `skins.applied.other` | `{player}`, `{skin}`. |
| `skins.removed.other` | `{player}`. |
| `skins.none_applied.other` | `{player}`. |
| `skins.notify_target_applied` | `{skin}`. |
| `skins.notify_target_removed` | none. |
| `skins.invalid_name` | `{skin}` trimmed input. |
| `skins.player_not_found` | `{player}` exact input. |
| `skins.lookup_failed` | `{skin}` input. |
| `skins.cooldown_active` | `{seconds}` whole seconds remaining. |
| `skins.working` | `{skin}`. |
| `skins.removing` | self removal only. |

## Persistence, privacy and network behavior

There is no database, DataRegistry, Redis or ProxyFeatures contract. Every apply operation sends the requested Minecraft account name and later UUID to Mojang's public services as required for official textures.

Logs record actor name, target name and requested/canonical skin name. Operators should account for that in log retention/privacy policy.

No signed texture payload is logged or persisted by this feature.

## Important implementation boundaries

- Custom state and cooldown are local and reset on quit/reload.
- Only online targets are supported.
- Staff operations have no cooldown.
- Removal depends on local state, not actual profile inspection.
- Failed official restore can still clear state and report success.
- Concurrent requests have no generation ordering.
- HTTP retries can exceed the source comment's stated total.
- Cache keys for names are case-sensitive and expired entries are not swept.
- The feature does not preserve the pre-update texture property locally.
- No permission restricts specific donor names.
- No profile update is persisted to Mojang or the player's account.
- The custom event is post-update and non-cancellable.
- Client refresh behavior depends on Paper's `setPlayerProfile` implementation and related visual listeners.

## Verification checklist

1. Apply a known valid donor skin to self and staff-target paths; verify canonical name feedback.
2. Test invalid syntax, nonexistent account, 204/404, 429 and simulated 5xx/timeouts.
3. Measure request count/backoff to confirm the effective retry loop.
4. Test self cooldown reservation, bypass, failure clearing, success retention and quit reset.
5. Start two applies for the same target with different donors and observe completion ordering.
6. Disconnect target during lookup and verify no profile mutation occurs.
7. Disconnect actor while a staff request remains in flight and inspect message/log behavior.
8. Remove after success and verify own official signed textures plus REMOVE event.
9. Block Session Server during removal and verify the documented state/success inconsistency.
10. Confirm Nametags receives `SkinUpdateEvent` and refreshes without detached/flickering tags.
11. Repeat donor names with case variants and observe separate name-cache entries.
12. Disable/reload during an in-flight lookup and validate lifecycle task fencing.

## Source map

- Defaults/messages/lifecycle: `features/skins/Skins.java`
- Command and permissions: `features/skins/command/SkinsCommand.java`
- HTTP, cache, profile mutation and cooldown workflow: `features/skins/service/SkinService.java`
- Transient state: `features/skins/internal/SkinState.java`
- Quit cleanup: `features/skins/listener/SkinsListener.java`
- Post-update contract: `features/skins/event/SkinUpdateEvent.java`, `SkinUpdateType.java`
