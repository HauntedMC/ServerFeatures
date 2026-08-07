# Economy network deployment checklist

Use this checklist before enabling Economy on a production HauntedMC network.

## Required shared infrastructure

- Every Paper instance must use the same authoritative MySQL database for shared/global currencies.
- Redis messaging should use the same configured connection and channel on all participating instances. Redis is an invalidation and notification transport only; MySQL remains authoritative.
- DataRegistry must resolve the same immutable player ID and UUID pair on every instance.
- Every participating instance must run the same ServerFeatures build. Do not operate mixed Economy schema or messaging versions during rollout.
- Keep every MySQL node synchronized to reliable time. Economy uses the database clock for mutation timestamps, cooldowns and UTC daily-limit buckets.

## Stable scope keys

- Give every logical gamemode one permanent local key, such as `survival`, `skyblock`, or `kitpvp`.
- Set the top-level `server_key`, `gamemode_key`, or `local_key` to that logical gamemode key. A per-currency `scope.local_key` may override it when replicas need an explicitly shared local scope.
- Physical replicas of one gamemode must use the same logical local key when they should share gamemode-local balances.
- Do not rename a logical key after accounts exist. A different key intentionally selects a different account scope.
- Configure Crowns and Credits as `GLOBAL` on every participating gamemode.
- Configure Essence, Relics, Soulstones, and Money as gamemode-local `SERVER` currencies.
- Expose only gamemode-local Money as the Vault primary currency on each Paper server.

## Configuration consistency

- Keep precision, starting balance, bounds, negative-balance policy, rounding, payment limits, cooldown, and payment-default settings identical for every server sharing a currency account scope.
- Keep each currency ID in one scope family across the network. For example, `crowns` must not be global on one gamemode and local on another.
- Startup must remain blocked when the persisted currency-family or scope definition conflicts with configuration. Do not bypass these guards.
- The MySQL definition is persistent: stopping an old server does not make a new monetary definition acceptable. A changed monetary definition needs a coordinated, reviewed data migration; see [Currency configuration and network consistency](economy.md#currency-configuration-and-network-consistency).
- Display formatting and local command/Vault settings are not part of the monetary definition guard. Keep them aligned as well unless a per-server difference is intentional.
- A new `GLOBAL` or `GROUP` currency created on one server becomes discoverable after that server starts successfully. Other servers may use `/economy definitions import ...` to generate a local policy-matching scaffold, then review and roll it out normally; import never reloads Economy automatically.

## Rollout

1. Back up the economy database and retain the exact configuration revision.
2. Deploy the same plugin build and the same configuration revision to every participating Paper instance.
3. If the release changes monetary policy, follow the coordinated migration procedure in the Economy guide instead of a rolling deployment.
4. Enable and validate one non-production gamemode first.
5. Run `/economy status`, `/economy currencies`, and `/economy verify`.
6. Test one local Money transfer between physical replicas of the same gamemode.
7. Test one local currency transaction while the recipient is online on another gamemode; only the originating gamemode account should change.
8. Test one global Crowns or Credits payment while the recipient is online on another gamemode; both servers should converge on the same committed MySQL balance.
9. Repeat the global test with Redis temporarily unavailable. The transaction must still commit safely and the remote display must heal through authoritative refresh after messaging returns or the refresh interval elapses.
10. Retry one native mutation with the same source and idempotency key. It must replay the original operation without changing the balance twice.
11. Retry the same key with a different amount or recipient. It must return an idempotency conflict and apply nothing.
12. Confirm Vault reports the correct gamemode-local Money balance and cannot expose Crowns, Credits, or other named currencies.
13. Enable the remaining gamemodes only after all checks pass.

## Operational rules

- Never repair balances by editing cache or Redis data.
- Never change balances outside `EconomyApi`, the Economy administration commands, or the registered Vault provider.
- Give website and other cross-process actors no direct write permission on Economy tables. Route them through an authenticated and authorized mutation gateway that preserves the native idempotency and transaction contract.
- Persist each external operation's idempotency key before dispatch, reuse it after every uncertain result, and make non-currency fulfillment independently idempotent or durable.
- Preserve operation IDs, sources, and idempotency keys in logs for every high-value integration.
- Keep financial data models immutable at API boundaries and retain null-safe failure handling in every compensation path.
- Treat `/economy verify` findings, identity conflicts, currency-definition conflicts, or repeated temporary database failures as release-blocking incidents.
