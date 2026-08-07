# Economy

Economy is ServerFeatures' authoritative multi-currency balance service. It is designed for one
network with multiple Paper servers and gamemodes. MySQL owns every balance and every committed
mutation. Redis is used only for cache invalidation and player-notification hints; it is never
trusted as a source of money.

This guide describes the normal operating model. Read [the deployment checklist](economy-deployment-checklist.md)
before enabling it on a network and [the incident runbook](economy-incident-response.md) when
something is already wrong.

## Mental model

Economy separates three concerns:

| Concern | Source of truth | Why it exists |
| --- | --- | --- |
| Accounts, settings and journal | MySQL | Correct balances, durable history and atomic changes across every server. |
| Online-player cache | Memory on each Paper server | Fast display and PlaceholderAPI reads. It is disposable and never writes money back. |
| Redis messages | Shared message channel | Prompt other servers to refresh an affected online player's cache and show a verified payment notification. |

The most important rule is: a displayed value can be cached, but a balance change is never cached.
Every successful change has committed to MySQL and has an immutable journal record first.

### Lifecycle on each Paper server

1. Economy loads and validates its local configuration.
2. It connects to MySQL and verifies that every currency definition is compatible with the
   definition already recorded for the network.
3. Only after that succeeds, it registers the native API, commands, join/quit listener,
   PlaceholderAPI expansion and optional Vault provider.
4. A player's join triggers an asynchronous preload of all configured accounts for that player.
   Their cached accounts are removed when they leave this Paper server.
5. The cache refreshes online players from MySQL periodically. Redis invalidations can trigger an
   earlier refresh, but a missed or duplicated Redis message cannot change a balance incorrectly.
6. On disable, Economy unregisters optional integrations and clears the local cache. Nothing is
   written from cache during shutdown.

The join/quit listener only maintains this local display cache. It does not create a second balance
store, validate configuration, or decide which account a player owns.

### How a balance change works

For a command, native API call or Vault call, Economy resolves the player identity, selects the
configured currency and its scope, then runs an indexed MySQL transaction. For a transfer, it locks
both account rows in deterministic player-ID order; it then checks account state and payment policy,
updates both balances, writes the journal, and commits as one unit. A success response is sent only
after that commit.

After commit, the initiating server updates its local view and may publish a Redis hint. A receiving
server reloads the account from MySQL before using that hint for a notification. This is why Redis
outages can delay display updates but cannot duplicate, lose, or manufacture money.

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

Economy uses DataRegistry's canonical `player_entity` identity. Economy accounts are keyed by its stable `player_id`; it does not maintain a second identity table.

An account is created lazily when a strong read or mutation first needs it. Its configured starting
balance is applied once in the same transaction as its first immutable journal entry. A player
therefore does not receive a second starting balance by joining another server, refreshing a cache,
or retrying an operation.

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

### Configuration reference

Economy reads the configuration once when the feature starts. Changing a file does not change a
running server; restart the feature/server as part of a controlled rollout.

| Setting | Meaning |
| --- | --- |
| `network_key` | Permanent identifier for this economy network. Changing it selects completely separate scopes. |
| `server_key` | Logical gamemode key used by `SERVER` currencies. Use the same value for physical replicas that must share a gamemode-local balance. |
| `currencies.<id>.enabled` | Whether this server exposes and validates that currency. Disabling it hides the currency here; it does not delete accounts or definitions. |
| `definition.scope.type` | `SERVER`, `GROUP`, or `GLOBAL`; immutable scope identity for the currency. |
| `definition.scope.local_key` / `group_key` | Stable override for a local or group scope. These are identifiers, not cosmetic names. |
| `definition.fractional_digits` | Immutable stored precision for the currency. |
| `definition.balances.*` | Immutable starting balance and balance range. Starting balance is applied once only. |
| `display.*` | Player-facing singular/plural names, symbol and formatting. |
| `payments.*` | Player-payment policy. A zero maximum, confirmation threshold, or daily limit means that particular upper/confirmation/limit check is disabled. |
| `commands.*` | The player command root, aliases and enabled subcommands for this server. Disabled commands are not registered. |
| `cache.authoritative_refresh_interval` | How often this Paper server refreshes online-player display data from MySQL. It must be between one second and five minutes. |
| `messaging.*` | Optional Redis invalidation/notification transport. It improves freshness but does not affect transaction correctness. |
| `vault.*` | Whether this Paper server registers one configured currency with Vault and how it handles another active provider. |

Amounts are parsed and normalized to the configured fractional precision and rounding mode. Invalid
configuration—such as a starting balance outside its bounds, a payment maximum below the minimum,
or enabling `paytoggle` while `pay` is disabled—prevents Economy from starting.

Only `definition.*` is immutable and must match where a scope is shared. `display.*`,
`payments.*` and `commands.*` are local policy and may differ per gamemode. Use the documented
`definition.*` paths for immutable monetary settings.

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
    definition:
      scope:
        type: SERVER
        local_key: survival
```

Both replicas then use `hauntedmc/server/survival` for Money. A wrong logical key intentionally creates a different account, so these keys must be managed as stable identifiers.

For a grouped currency:

```yaml
definition:
  scope:
    type: GROUP
    group_key: survival-network
```

### Multi-instance Survival group example

Use `GROUP` when a currency should be shared by a defined set of servers, without making it
network-global. For example, Survival can run two independently named Paper instances while
sharing `survival_tokens`; Skyblock remains outside that group.

`survival-1`:

```yaml
network_key: hauntedmc
server_key: survival-1

currencies:
  survival_tokens:
    enabled: true
    definition:
      scope:
        type: GROUP
        group_key: survival
      fractional_digits: 0
    display:
      singular: survival token
      plural: survival tokens
      symbol: ""
      format: "{amount} {plural}"
    # Display, commands and payments may be chosen locally.
```

`survival-2` uses the same currency definition and group key, but keeps its own physical/logical
server key:

```yaml
network_key: hauntedmc
server_key: survival-2

currencies:
  survival_tokens:
    enabled: true
    definition:
      scope:
        type: GROUP
        group_key: survival
      fractional_digits: 0
    # Copy the same definition; display, commands and payments may differ.
```

Both resolve this account scope to `hauntedmc/group/survival`, so a player's `survival_tokens`
balance is shared between them. A Skyblock server either omits this currency or configures a
different group key, so it does not access that balance. A `GLOBAL` currency still resolves to
`hauntedmc/global` everywhere.

If the two servers are simply identical physical replicas and **all** gamemode-local currencies
should be shared, it is usually simpler to give both `server_key: survival` and keep those
currencies as `SERVER`. Use `GROUP` when the membership should be explicit or only selected
currencies should be shared.

## Currency configuration and network consistency

### What must match

A currency is identified by its currency ID and resolved scope key. For example, a global
`crowns` currency on the `hauntedmc` network always resolves to `hauntedmc/global`, regardless of
which Paper server serves the request. Servers that use that account must agree on its **monetary
definition**:

- scope type and resolved scope key;
- fractional precision;
- starting, minimum and maximum balances; and
- negative-balance policy and rounding mode.

On first startup, Economy stores a fingerprint of this definition in MySQL. Every later startup
locks and compares that stored definition before Economy becomes available. The database record is
the guard: it does not matter whether the server that originally created it is still online.

| Situation | Result |
| --- | --- |
| A new server has the same monetary definition | It starts and shares the existing accounts. |
| A server has a non-matching monetary definition | Only that currency is skipped; other valid currencies, API and administration remain available. |
| An already-running server has a non-matching definition | It continues using its in-memory configuration until it is stopped or restarted. It is not changed remotely. |
| Only display labels, payment limits/defaults/cooldowns, commands, or Vault enablement differ | Startup is permitted; these are local operational policy. |

This prevents a newly starting server from applying incompatible precision or balance bounds to the
same account. Payment policy can be rolled out independently per gamemode. It does not magically
reconfigure a server that was already running when a rollout began.

### Safe rollout and configuration changes

Use one config revision for every server that shares an account scope. A normal release that does
not change a monetary definition can be rolled out according to the deployment checklist. A
definition mismatch is a release-blocking safety stop, not an error to bypass. Keep Economy off on
that server, compare its resolved configuration with the stored/network configuration, and follow
the incident runbook.

### Discovering and importing shared currencies

When the first server starts a new enabled currency, Economy stores both its immutable fingerprint
and a canonical **monetary-definition payload** in MySQL. The payload makes a global or
group currency discoverable by other servers without trying to reverse a hash.

Administrators with `serverfeatures.feature.economy.admin.definitions` can inspect shared
definitions:

```text
/economy definitions list
/economy definitions show <currency> <resolved-scope-key>
```

Writing a local configuration scaffold additionally requires
`serverfeatures.feature.economy.admin.definitions.import`:

```text
/economy definitions import <currency> <resolved-scope-key>
/economy definitions import <currency> <resolved-scope-key> confirm
```

`list` shows discoverable `GLOBAL` and `GROUP` definitions for the configured `network_key`. Copy
the scope key from this output into `show` or `import`; examples are `hauntedmc/global` and
`hauntedmc/group/survival`. `show` displays policy values only—never balances, identities or
transaction data.

The first `import` is a dry run. `confirm` writes a missing-only currency scaffold to this server's
Economy YAML; it never overwrites an existing currency, never changes MySQL, and never reloads a
running feature. Review the saved config and deploy/reload it through the normal network rollout
process. The generated scaffold preserves the authoritative scope, precision, balance and payment
policy, but deliberately uses local defaults for display and commands. It enables balance/history
commands and leaves player `pay`/`paytoggle` disabled until a local administrator explicitly
reviews and enables them. Vault selection remains local as well.

Definition rows without a canonical payload cannot be imported. Never guess missing policy values
or edit definition rows by hand.

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
- Journal operation types describe generic money movement and account-state changes only. Integrations identify their own domain action through the stable source, reason, and metadata fields.
- Transfers lock both accounts in canonical player-ID order.
- Concurrent withdrawals cannot spend the same balance twice.
- Account creation is protected by deterministic IDs and database uniqueness, so starting balances are applied once.
- Account creation, including a zero or non-zero starting balance, receives its own immutable journal entry.
- Accounts are keyed only by DataRegistry's immutable `player_id`; Economy does not maintain a
  second player-identity table.
- Payment cooldowns and daily send/receive limits are stored and checked transactionally, so switching gamemodes cannot bypass them for a shared currency.
- All persisted Economy timestamps, payment cooldowns and UTC daily-limit buckets use the authoritative MySQL clock, so Paper-server clock skew cannot corrupt journal ordering or open a second spending window.
- Account freezes and payment preferences are scoped with the account and are themselves audited transactions.
- Each currency's `definition` section is fingerprinted in MySQL. A conflicting scope, precision,
  balance policy or rounding policy rejects only that currency; payment limits, command settings
  and display settings are operational settings and may be changed normally.

## Idempotency

Native callers provide both a stable `source` and an `idempotencyKey`. The unique pair identifies one logical request across the whole network.

The stored request fingerprint binds the operation type, account or transfer parties, scope, normalized amount, actor, reason, metadata and bypass policy. Reusing the same key for the same request returns the original operation as `IDEMPOTENT_REPLAY`. Reusing it for a different request returns `IDEMPOTENCY_CONFLICT`; the second request is not applied.

Integration sources should be globally stable names such as `shop`, `quest-rewards`, or `website-store`. Retry attempts for one logical operation must reuse the same idempotency key.

## Storage

The feature registers these ORM entities:

- `economy_currency_family`
- `economy_currency_definition`
- `player_economy_balance`
- `player_economy_settings`
- `economy_transaction`
- `economy_transaction_entry`
- `economy_workflow`
- `player_economy_daily_usage`

Economy's database placement is fixed and does not need a feature-level database setting. System
tables use the `economy_` prefix; player-state tables use the `player_economy_` prefix.

`/economy verify` is read-only. It checks balance bounds, journal arithmetic and continuity, transaction shapes, entry/account ownership, current balances against the latest journal entries, orphaned settings/entries and transactions without entries; it never repairs or rewrites balances. This lets a generic Economy version continue to verify immutable journals created by an older integration-specific version without retaining that integration's taxonomy in Economy code.

## Failure behavior

- MySQL unavailable: mutations fail closed and no success is returned.
- Redis unavailable: committed transactions continue safely; caches heal from MySQL on their configured refresh interval.
- Duplicate/reordered Redis delivery: versioned invalidation and authoritative reload prevent stale overwrites.
- Server crash after commit: the balance and journal remain committed; idempotent callers can safely replay the request.
- Transient connection loss during commit: the repository retries the same idempotent request, so an uncertain commit resolves to either the original journaled operation or one new commit.
- Built-in integrations retry an uncertain operation with the exact same idempotency key, so a timeout cannot charge or pay twice.
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
/<currency> help
```

Disabled subcommands are absent from Brigadier suggestions.

`pay` accepts positive decimal values only and normalizes them to the currency's configured
precision. Economy rechecks the payment minimum/maximum, sender balance, account freeze,
recipient payment preference, cooldown and daily limits inside the final MySQL transaction; command
validation is never the only protection. When `confirmation_threshold` is positive, a payment at or
above that amount requires `confirm`. The pending confirmation belongs to one player, replaces any
older pending payment, and expires after 30 seconds.

Player command permissions can be granted per currency with
`serverfeatures.feature.economy.currency.<currency>.<action>` or across currencies with
`serverfeatures.feature.economy.<action>`. The currency-wide and base Economy permissions also
grant their relevant currency actions. Valid player actions are `balance`, `balance.others`, `pay`,
`paytoggle`, `history`, and `top`.

## Administration

```text
/economy status
/economy help
/economy currencies
/economy definitions list
/economy definitions show <currency> <resolved-scope-key>
/economy definitions import <currency> <resolved-scope-key> [confirm]
/economy balance <player> <currency>
/economy account <player> <currency>
/economy add <player> <currency> <amount> <reason...>
/economy remove <player> <currency> <amount> <reason...>
/economy set <player> <currency> <amount> <reason...>
/economy payments <player> <currency> <on|off> <reason...>
/economy freeze <player> <currency> <reason...>
/economy unfreeze <player> <currency> <reason...>
/economy history <player> <currency> [page]
/economy verify
```

Administrative balance changes require a reason, are journaled, and return an operation ID.
Administrative permissions use `serverfeatures.feature.economy.admin.<action>`; definition
inspection uses `definitions`, while the configuration-writing import uses `definitions.import`.
`serverfeatures.feature.economy.admin` grants all administration actions. Treat balance changes,
freeze changes and payment-preference changes as audited operational actions, not routine player
support shortcuts.

## Vault

Vault is optional and exposes exactly one configured primary currency per Paper server. For HauntedMC this should be gamemode-local `money`:

```yaml
vault:
  enabled: true
  primary_currency: money
```

Standard Vault does not tell an economy provider which consuming plugin made a call, so it cannot route different third-party plugins to different currencies on the same server. HauntedMC integrations that need Crowns, Credits, Essence, Relics or Soulstones must use the native `EconomyApi`.

Vault calls are synchronous by contract. The adapter performs a bounded persisted DataRegistry identity lookup when required and a short indexed MySQL operation. It returns success only after commit and converts lookup/database failures into failed `EconomyResponse` values. It never acknowledges a queued write. External Vault providers do not provide caller idempotency, so the built-in native API remains preferable for high-value HauntedMC operations. Because Vault represents amounts as `double`, Economy refuses to register a primary currency whose configured range cannot distinguish every smallest currency unit in IEEE-754; use the native API/gateway for larger real-value ranges.

Conflict policies:

- `FAIL`: reject Vault registration if another provider is active.
- `SKIP`: leave the other provider active while native Economy remains available.
- `REPLACE`: explicitly register ServerFeatures Economy at highest priority.

Registration fails closed unless Vault reports the new ServerFeatures provider as active. `REPLACE` cannot update another plugin that cached a provider before Economy loaded, so production deployments should load Economy before consumers and prefer `FAIL` when provider ownership is uncertain.

## Native API

`EconomyApi` is registered through the feature service catalog. All native mutations are asynchronous and explicitly select a currency/account scope. Strong balance reads and all mutations use MySQL; cache reads are an optional display optimization only.

`EconomyApi` is an in-process Java API, not a website endpoint. A website or another external process must use an authenticated, authorized server-side gateway that delegates to this API, or a separately reviewed service that preserves the identical MySQL row-lock, journal and idempotency transaction. Do not give external applications direct write access to Economy tables.

The gateway must persist a stable idempotency key before sending a logical operation, reuse the exact source/key/request after timeouts or connection loss, and return success only after the authoritative mutation commits. A balance read is never a reservation: purchase flows must perform the withdrawal atomically, then deliver the purchased item idempotently or through a durable outbox/saga so application retries cannot charge or fulfill twice.

## PlaceholderAPI

Every enabled currency exposes the following cache-only placeholders, replacing `<currency>` with its ID:

| Placeholder | Value when account is cached | Value when unavailable/no player context |
|---|---|---|
| `%economy_<currency>_balance%` | Formatted balance | `0` |
| `%economy_<currency>_raw%` | Plain decimal balance | `0` |
| `%economy_<currency>_available%` | `true` | `false` |
| `%economy_<currency>_payments%` | Current incoming-payment setting | Configured `payments.default_enabled` |
| `%economy_<currency>_frozen%` | Whether the account is frozen | `false` |
| `%economy_<currency>_status%` | `active` or `frozen` | `unavailable` |

Currency metadata does not require a player/cache entry: `%economy_<currency>_scope%`, `_scope_type`, `_currency`, `_symbol`, `_singular`, `_plural`, and `_fractional_digits`.

The configured Vault primary currency supports the same suffixes through `%economy_primary_<suffix>%`; for example `%economy_primary_balance%` and `%economy_primary_available%`.

Placeholder evaluation never blocks the Paper thread, resolves no identities, and issues no MySQL or Redis calls. Online-player cache entries are refreshed from authoritative MySQL periodically and after network invalidations. Use `available` to distinguish an unavailable cache entry from a genuine zero balance. Unknown/malformed placeholders return `null` to PlaceholderAPI.
