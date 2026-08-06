# Economy

Economy is the authoritative ServerFeatures multi-currency balance service. MySQL owns every balance and every committed mutation. Redis is used only for invalidation and notification hints; it is never trusted as a source of money.

## Currency scopes

Each currency resolves to one stable account scope:

- `SERVER`: local to one **logical gamemode key**. Despite the enum name, this is not required to be a physical Paper instance. Replicas such as `survival-1` and `survival-2` should use the same logical key when they must share the same gamemode-local balances.
- `GROUP`: shared by every server using the configured group key.
- `GLOBAL`: one balance for the whole configured network.

The durable account identity is:

```text
DataRegistry player ID + currency ID + resolved scope key
```

A player can therefore have one global `crowns` account and separate `money` accounts for Survival, Skyblock and KitPvP at the same time.

A dedicated `player_economy_identity` table permanently binds every economy player ID to exactly one UUID, and every UUID to exactly one player ID, before any account is created. Player names remain display metadata only. Any identity conflict fails closed across all currencies and scopes instead of reassigning value.

## HauntedMC topology

The intended HauntedMC setup is supported without special-case code:

| Currency | Scope | Result |
| --- | --- | --- |
| Crowns | `GLOBAL` | Same balance everywhere |
| Credits | `GLOBAL` | Same balance everywhere |
| Essence | `SERVER` | Separate balance for each gamemode |
| Relics | `SERVER` | Separate balance for each gamemode |
| Soulstones | `SERVER` | Separate balance for each gamemode |
| Money | `SERVER` | Separate balance for each gamemode; exposed through Vault |

The same model supports future currencies in `SERVER`, `GROUP` or `GLOBAL` scope.

### Survival example

```yaml
network_key: hauntedmc
# This is the logical gamemode key, not necessarily the physical instance name.
server_key: survival

database:
  connection: system_data_rw

messaging:
  enabled: true
  connection: hauntedmc
  channel: serverfeatures.economy.balance

cache:
  # Authoritative MySQL refresh for online players. This heals missed Redis messages.
  authoritative_refresh_interval: 10s

vault:
  enabled: true
  primary_currency: money
  conflict_policy: FAIL

currencies:
  crowns:
    scope:
      type: GLOBAL
    display:
      singular: crown
      plural: crowns
      symbol: ""
      format: "{amount} {plural}"
      fractional_digits: 0
      grouping: true
    balances:
      starting: "0"
      minimum: "0"
      maximum: "100000000"
      allow_negative: false
      rounding: DOWN
    commands:
      root: crowns
      aliases: []
      balance: true
      balance_others: true
      pay: true
      paytoggle: true
      history: true
      top: true
    payments:
      default_enabled: true
      minimum: "1"
      maximum: "100000"
      confirmation_threshold: "10000"
      daily_send_limit: "0"
      daily_receive_limit: "0"
      cooldown: 1s

  credits:
    scope:
      type: GLOBAL
    display:
      singular: credit
      plural: credits
      symbol: ""
      format: "{amount} {plural}"
      fractional_digits: 0
      grouping: true
    balances:
      starting: "0"
      minimum: "0"
      maximum: "100000000"
      allow_negative: false
      rounding: DOWN
    commands:
      root: credits
      aliases: []
      balance: true
      balance_others: true
      pay: true
      paytoggle: true
      history: true
      top: true
    payments:
      default_enabled: true
      minimum: "1"
      maximum: "100000"
      confirmation_threshold: "10000"
      daily_send_limit: "0"
      daily_receive_limit: "0"
      cooldown: 1s

  essence:
    scope:
      type: SERVER
    display:
      singular: essence
      plural: essence
      symbol: ""
      format: "{amount} {plural}"
      fractional_digits: 0
      grouping: true
    balances:
      starting: "0"
      minimum: "0"
      maximum: "100000000"
      allow_negative: false
      rounding: DOWN
    commands:
      root: essence
      aliases: []
      balance: true
      balance_others: true
      pay: false
      paytoggle: false
      history: true
      top: true
    payments:
      default_enabled: false
      minimum: "1"
      maximum: "0"
      confirmation_threshold: "0"
      daily_send_limit: "0"
      daily_receive_limit: "0"
      cooldown: 0ms

  relics:
    scope:
      type: SERVER
    display:
      singular: relic
      plural: relics
      symbol: ""
      format: "{amount} {plural}"
      fractional_digits: 0
      grouping: true
    balances:
      starting: "0"
      minimum: "0"
      maximum: "100000000"
      allow_negative: false
      rounding: DOWN
    commands:
      root: relics
      aliases: []
      balance: true
      balance_others: true
      pay: false
      paytoggle: false
      history: true
      top: true
    payments:
      default_enabled: false
      minimum: "1"
      maximum: "0"
      confirmation_threshold: "0"
      daily_send_limit: "0"
      daily_receive_limit: "0"
      cooldown: 0ms

  soulstones:
    scope:
      type: SERVER
    display:
      singular: soulstone
      plural: soulstones
      symbol: ""
      format: "{amount} {plural}"
      fractional_digits: 0
      grouping: true
    balances:
      starting: "0"
      minimum: "0"
      maximum: "100000000"
      allow_negative: false
      rounding: DOWN
    commands:
      root: soulstones
      aliases: []
      balance: true
      balance_others: true
      pay: false
      paytoggle: false
      history: true
      top: true
    payments:
      default_enabled: false
      minimum: "1"
      maximum: "0"
      confirmation_threshold: "0"
      daily_send_limit: "0"
      daily_receive_limit: "0"
      cooldown: 0ms

  money:
    scope:
      type: SERVER
    display:
      singular: coin
      plural: coins
      symbol: "$"
      format: "{symbol}{amount}"
      fractional_digits: 2
      grouping: true
    balances:
      starting: "0.00"
      minimum: "0.00"
      maximum: "999999999999.99"
      allow_negative: false
      rounding: HALF_UP
    commands:
      root: money
      aliases: [balance, bal]
      balance: true
      balance_others: true
      pay: true
      paytoggle: true
      history: true
      top: false
    payments:
      default_enabled: true
      minimum: "0.01"
      maximum: "1000000.00"
      confirmation_threshold: "100000.00"
      daily_send_limit: "0.00"
      daily_receive_limit: "0.00"
      cooldown: 1s
```

Use the same currency definitions on Skyblock, KitPvP and other gamemodes, but set their top-level logical key accordingly:

```yaml
server_key: skyblock
```

This makes `crowns` and `credits` resolve to `hauntedmc/global`, while local currencies resolve to `hauntedmc/server/skyblock`.

When multiple physical instances serve one gamemode, either give each instance the same top-level `server_key`, or override an individual currency explicitly:

```yaml
server_key: survival-1
currencies:
  money:
    scope:
      type: SERVER
      local_key: survival
```

Both replicas then use `hauntedmc/server/survival` for Money. A wrong logical key intentionally creates a different account, so these keys must be managed as stable identifiers.

For a grouped currency:

```yaml
scope:
  type: GROUP
  group_key: survival-network
```

## Network-wide transfer behavior

A global payment from gamemode A to a player online on gamemode B follows this path:

1. A resolves both canonical DataRegistry identities.
2. MySQL locks both global account rows in deterministic order.
3. Balance, paytoggle, account status, cooldown and daily limits are rechecked while locked.
4. Sender debit, recipient credit and both immutable journal entries commit in one MySQL transaction.
5. A returns success only after commit.
6. Redis publishes an invalidation hint and the committed operation ID.
7. B verifies that operation against the MySQL transaction journal and reloads the recipient account from MySQL before displaying the notification.
8. Periodic authoritative refresh heals the cache if Redis is unavailable or a message is missed.

Redis messages never contain an authoritative balance and cannot mint, remove or overwrite money. A duplicated message can only cause a redundant refresh; notification operation IDs are deduplicated.

For a local currency, a transfer initiated on Survival affects the recipient's Survival-scoped account even when that recipient is currently on Skyblock. It does not alter their Skyblock account. The balance becomes visible immediately when the recipient is on a server using the same local scope, or on the next authoritative refresh/join when they return to that gamemode.

Known offline players remain valid payment recipients. This cannot be disabled per server, because different servers cannot reliably distinguish “offline” from “online elsewhere” without making monetary behavior depend on presence races.

## Transaction guarantees

- MySQL is authoritative; there is no local-file fallback and no write-behind balance queue.
- Every mutation and its journal entries commit atomically.
- Transfers lock both accounts in canonical player-ID order.
- Concurrent withdrawals cannot spend the same balance twice.
- Account creation is protected by deterministic IDs and database uniqueness, so starting balances are applied once.
- Account creation, including a zero or non-zero starting balance, receives its own immutable journal entry.
- Player ID and UUID ownership is immutable; an identity mismatch fails closed instead of reassigning an account.
- Payment cooldowns and daily send/receive limits are stored and checked transactionally, so switching gamemodes cannot bypass them for a shared currency.
- Account freezes and payment preferences are scoped with the account and are themselves audited transactions.
- Monetary configuration for a shared scope is fingerprinted in MySQL. Servers with conflicting precision, bounds, starting balance, negative policy, rounding or payment policies fail startup instead of running a split-brain currency.

## Idempotency

Native callers provide both a stable `source` and an `idempotencyKey`. The unique pair identifies one logical request across the whole network.

The stored request fingerprint binds the operation type, account or transfer parties, scope, normalized amount, actor, reason, metadata and bypass policy. Reusing the same key for the same request returns the original operation as `IDEMPOTENT_REPLAY`. Reusing it for a different request returns `IDEMPOTENCY_CONFLICT`; the second request is not applied.

Integration sources should be globally stable names such as `lottery`, `shop`, or `quest-rewards`. Retry attempts for one logical operation must reuse the same idempotency key.

## Storage

The feature registers these ORM entities:

- `system_economy_currency_family`
- `system_economy_currency_definition`
- `player_economy_identity`
- `player_economy_balance`
- `player_economy_settings`
- `system_economy_transaction`
- `system_economy_transaction_entry`
- `player_economy_daily_usage`

`/economy verify` is read-only. It checks balance bounds, journal arithmetic, orphaned settings/entries and transactions without entries; it never repairs or rewrites balances.

## Failure behavior

- MySQL unavailable: mutations fail closed and no success is returned.
- Redis unavailable: committed transactions continue safely; caches heal from MySQL on their configured refresh interval.
- Duplicate/reordered Redis delivery: versioned invalidation and authoritative reload prevent stale overwrites.
- Server crash after commit: the balance and journal remain committed; idempotent callers can safely replay the request.
- Lottery built-in backend: one automatic retry reuses the exact same idempotency key, so an uncertain first response cannot charge or pay twice.
- Server crash before commit: the transaction rolls back.

## Player commands

Each currency registers only its enabled command tree:

```text
/<currency>
/<currency> balance [player]
/<currency> pay <player> <amount>
/<currency> confirm
/<currency> paytoggle [on|off|status]
/<currency> history [page]
/<currency> top [page]
```

Disabled subcommands are absent from Brigadier suggestions.

## Administration

```text
/economy status
/economy currencies
/economy balance <player> <currency>
/economy add <player> <currency> <amount> <reason...>
/economy remove <player> <currency> <amount> <reason...>
/economy set <player> <currency> <amount> <reason...>
/economy payments <player> <currency> <on|off>
/economy freeze <player> <currency> <reason...>
/economy unfreeze <player> <currency> <reason...>
/economy history <player> <currency> [page]
/economy verify
```

Administrative balance changes require a reason, are journaled, and return an operation ID.

## Vault

Vault is optional and exposes exactly one configured primary currency per Paper server. For HauntedMC this should be gamemode-local `money`:

```yaml
vault:
  enabled: true
  primary_currency: money
```

Standard Vault does not tell an economy provider which consuming plugin made a call, so it cannot route different third-party plugins to different currencies on the same server. HauntedMC integrations that need Crowns, Credits, Essence, Relics or Soulstones must use the native `EconomyApi`.

Vault calls are synchronous by contract. The adapter performs a bounded persisted DataRegistry identity lookup when required and a short indexed MySQL operation. It returns success only after commit and converts lookup/database failures into failed `EconomyResponse` values. It never acknowledges a queued write. External Vault providers do not provide caller idempotency, so the built-in native API remains preferable for high-value HauntedMC operations.

Conflict policies:

- `FAIL`: reject Vault registration if another provider is active.
- `SKIP`: leave the other provider active while native Economy remains available.
- `REPLACE`: explicitly register ServerFeatures Economy at highest priority.

## Native API

`EconomyApi` is registered through the feature service catalog. All native mutations are asynchronous and explicitly select a currency/account scope. Strong balance reads and all mutations use MySQL; cache reads are an optional display optimization only.

## PlaceholderAPI

Cache-only placeholders include:

```text
%economy_money_balance%
%economy_money_raw%
%economy_money_scope%
%economy_money_payments%
%economy_primary_balance%
%economy_primary_raw%
```

Placeholder evaluation never blocks the Paper thread. Online-player cache entries are refreshed from authoritative MySQL periodically and after network invalidations.
