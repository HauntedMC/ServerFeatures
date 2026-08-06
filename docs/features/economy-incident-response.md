# Economy incident response

Use this runbook for any suspected balance, transaction, identity, MySQL, Redis or Vault incident.

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

## Recovery rules

- Restore or compensate value only through a reviewed, audited Economy operation with a mandatory reason and a retained operation ID.
- Never delete transaction or transaction-entry rows to hide an error.
- Never retry an uncertain high-value operation with a new idempotency key.
- Keep an account frozen until `/economy verify` is healthy and the responsible transaction chain is understood.
- Escalate identity mismatches, currency-definition conflicts, journal arithmetic failures or repeated transient database errors as release-blocking incidents.

## Post-incident validation

After remediation, rerun `/economy verify`, compare the account history with the intended business event, validate the balance from MySQL through the strong Economy API, and test one idempotent replay before unfreezing the account.
