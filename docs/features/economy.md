# Economy

Economy is the authoritative ServerFeatures multi-currency balance service. It stores every mutation in MySQL and exposes an asynchronous native API plus an optional Vault provider.

## Currency scopes

Every currency resolves to one stable scope:

- `SERVER`: one balance for each configured logical server key.
- `GROUP`: one balance shared by servers using the same group key.
- `GLOBAL`: one balance shared across the entire configured network key.

The account key is the player identity, currency ID and resolved scope key. Currency IDs and storage-defining properties must not be changed after accounts exist; incompatible definitions are rejected during startup.

## Storage

The feature registers these ORM entities:

- `system_economy_currency_definition`
- `player_economy_balance`
- `player_economy_settings`
- `system_economy_transaction`
- `system_economy_transaction_entry`
- `player_economy_daily_usage`

Balance updates, payment settings, daily-limit updates and audit rows are committed in one database transaction. Account rows are locked in deterministic order for transfers. Internal callers provide an idempotency key so a retry cannot apply the same logical transaction twice.

Redis messaging only updates caches after a successful commit. MySQL remains authoritative and a Redis outage cannot permit overspending or duplicate money.

## Configuration

```yaml
network_key: hauntedmc
server_key: "$server"

database:
  connection: system_data_rw

messaging:
  enabled: true
  connection: hauntedmc
  channel: serverfeatures.economy.balance

vault:
  enabled: true
  primary_currency: money
  conflict_policy: FAIL

currencies:
  money:
    enabled: true
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
      allow_offline_recipient: true
      minimum: "0.01"
      maximum: "1000000.00"
      confirmation_threshold: "100000.00"
      daily_send_limit: "0.00"
      cooldown: 1s

  network_points:
    enabled: true
    scope:
      type: GLOBAL
    display:
      singular: point
      plural: points
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
      root: points
      aliases: []
      balance: true
      balance_others: false
      pay: false
      paytoggle: false
      history: true
      top: true
    payments:
      default_enabled: false
      allow_offline_recipient: true
      minimum: "1"
      maximum: "0"
      confirmation_threshold: "0"
      daily_send_limit: "0"
      cooldown: 1s
```

For a group currency use:

```yaml
scope:
  type: GROUP
  group_key: survival-network
```

Multiple physical replicas may intentionally share the same logical `server_key`.

## Player commands

Each currency registers its configured command root and aliases. Enabled subcommands include:

```text
/<currency>
/<currency> balance [player]
/<currency> pay <player> <amount>
/<currency> confirm
/<currency> paytoggle [on|off|status]
/<currency> history [page]
/<currency> top [page]
```

Disabled subcommands are not registered and are absent from Brigadier suggestions.

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

Administrative changes are journaled and return an operation ID. `verify` is read-only and reports structural inconsistencies without changing balances.

## Vault

Vault is optional. When present, Economy can register one configured primary currency. That currency may be server-local, group-shared or global. Standard Vault cannot select a currency based on which shop plugin called it, so only one currency can be exposed by Vault on a Paper server.

Conflict policies:

- `FAIL`: reject startup if another provider is active.
- `SKIP`: retain the other provider while the native Economy API remains usable.
- `REPLACE`: register ServerFeatures Economy at the highest priority.

Vault methods are synchronous by contract. The adapter therefore commits short indexed database transactions before returning a successful response; it never acknowledges an asynchronous write-behind operation.

## Native API

`EconomyApi` is registered through the feature service catalog. Native mutations are asynchronous and accept source and idempotency identifiers. Custom HauntedMC features should use this API instead of Vault whenever they need explicit currency selection.

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

Placeholder evaluation never performs database I/O on the Paper thread.
