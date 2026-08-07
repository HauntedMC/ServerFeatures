# Economy incident response

Use this runbook for any suspected balance, transaction, identity, MySQL, Redis or Vault incident. Treat the transaction journal and operation IDs as the primary evidence chain.

For the normal architecture, account scopes, cache behavior and configuration-change model, read
[the Economy guide](economy.md) first. This document is for responding safely once an incident or
startup failure has occurred.

## Immediate containment

1. Do not edit balances, Redis data or ORM tables manually.
2. Disable the integration that is producing unexpected mutations, or disable Economy on the affected Paper instance when the source is unknown.
3. Freeze the affected account with `/economy freeze <player> <currency> <reason...>` when continued player access could increase the impact.
4. Record the player UUID, canonical player ID, currency, scope key, operation ID, source, idempotency key, server name and exact UTC time.
5. Preserve current application, MySQL and proxy logs before restarting services.

## Diagnosis

- Run `/economy status`, `/economy currencies` and `/economy verify`.
- Check that every participating Paper server runs the same ServerFeatures build and resolves the same logical scope keys.
- Check MySQL availability, replication health and clock synchronization before investigating Redis.
- Treat Redis as notification/cache infrastructure only. A Redis outage does not justify reverting a committed MySQL transaction.
- For a retried native integration call, verify that the source and idempotency key are unchanged. Reusing the key with a different request must return `IDEMPOTENCY_CONFLICT`.
- For a cross-server payment notification, verify the immutable transfer journal before trusting chat or cache observations.

## Currency-definition mismatch or configuration rollout failure

Economy records an immutable monetary-definition fingerprint in MySQL for each currency and scope.
A message such as `Currency definition mismatch` or `Currency family mismatch` means that the
affected currency is intentionally not loaded. Other valid currencies remain available on that
server; no mutation service is exposed for the rejected currency.

This is not controlled by whether an older server is currently online:

- The first successful startup created the MySQL definition record.
- A server with a different monetary definition cannot load that currency against that record.
- Stopping the old server does not remove or update the record.
- A server that was already running before a configuration rollout continues to use its old
  in-memory policy until stopped or restarted.

Respond as follows:

1. Do not delete or edit `system_economy_currency_definition` or
   `system_economy_currency_family` to make startup succeed.
2. Keep the mismatching currency disabled on that server. Do not route its mutations, Vault consumers or
   commands to it as though it had joined the shared economy.
3. Compare the resolved `network_key`, currency ID, scope type/key, fractional precision, balance
   policy, rounding, and payment policy with a known-good server and the intended release config.
4. If the server is simply stale, deploy the known-good config and restart it. The same fingerprint
   will then validate.
5. If the monetary policy is intentionally changing, stop Economy on every server sharing that
   scope, take a backup, and escalate it as a reviewed data migration. Scope changes, lower limits,
   precision reductions and negative-policy changes may require reconciling existing balances.
6. After the migration, start one controlled server, run `/economy verify`, test the changed policy,
   then bring up the remaining servers with the identical configuration.

Display-only differences (names, symbols, formatting/grouping) and local command/Vault choices do
not form part of the monetary fingerprint. They can start, but inconsistent player-facing behavior
is still usually a deployment mistake.

## Recovery rules

- Restore or compensate value only through a reviewed, audited Economy operation with a mandatory reason and a retained operation ID.
- Never delete transaction or transaction-entry rows to hide an error.
- Never retry an uncertain high-value operation with a new idempotency key.
- Keep an account frozen until `/economy verify` is healthy and the responsible transaction chain is understood.
- Escalate identity mismatches, currency-definition conflicts, journal arithmetic failures or repeated transient database errors as release-blocking incidents.

## Post-incident validation

After remediation, rerun `/economy verify`, compare the account history with the intended business event, validate the balance from MySQL through the strong Economy API, and test one idempotent replay before unfreezing the account.
