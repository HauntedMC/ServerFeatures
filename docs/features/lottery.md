# Lottery

> Paper · feature `Lottery` · disabled by default

Lottery provides scheduled ticket-based draws, optional donations, multiple prize shares, offline payouts, history, leaderboards and a small staff command set.

## Design

The feature deliberately uses the same persistence style as other ServerFeatures modules:

- one feature-owned `ORMContext` on `system_data_rw`;
- four normal JPA entities;
- short transactions through `ORMContext.runInTransaction`;
- no migration importer, state-binding layer, operation journal, maintenance service, review queue or reconciliation commands.

The four tables are:

- `system_lottery_rounds` — current and completed draws;
- `system_lottery_entries` — one row per player and round;
- `system_lottery_payouts` — pending, paying, paid or failed wins/refunds;
- `system_lottery_player_stats` — totals used by leaderboards.

`lottery_key` defaults to `$server`, so each backend receives an independent lottery. A round row is pessimistically locked while tickets, donations, drawing, pausing, cancellation or pot additions are changed. Marking a round `DRAWING` prevents a second server from drawing it twice.

Vault calls remain on the Paper main thread. A purchase or donation is withdrawn first and stored immediately afterwards. If the database write fails, the amount is refunded. Payout rows use `PENDING -> PAYING -> PAID`; a definite Vault failure returns the row to `PENDING`, while an uncertain result becomes `FAILED` and is logged instead of being repeated automatically.

## Player commands

| Command | Permission |
| --- | --- |
| `/lottery` | `serverfeatures.feature.lottery.use` |
| `/lottery buy [amount|max]` | `serverfeatures.feature.lottery.buy` |
| `/lottery donate <amount>` | `serverfeatures.feature.lottery.donate` |
| `/lottery claim` | `serverfeatures.feature.lottery.claim` |
| `/lottery history [page]` | `serverfeatures.feature.lottery.use` |
| `/lottery leaderboard <wins|donations> [page]` | `serverfeatures.feature.lottery.use` |

## Staff commands

| Command | Permission |
| --- | --- |
| `/lottery admin status` | `serverfeatures.feature.lottery.admin.inspect` |
| `/lottery admin pause` / `resume` | `serverfeatures.feature.lottery.admin.pause` |
| `/lottery admin addpot <amount>` | `serverfeatures.feature.lottery.admin.addpot` |
| `/lottery admin draw` | `serverfeatures.feature.lottery.admin.draw` |
| `/lottery admin cancel` | `serverfeatures.feature.lottery.admin.cancel` |

There are no maintenance, operations, reconciliation, import or compatibility commands.

## Configuration

```yaml
enabled: false
lottery_key: "$server"

schedule:
  mode: INTERVAL
  interval: 12h
  timezone: Europe/Amsterdam
  fixed_times: []

tickets:
  price: "500.00"
  maximum_per_player: 1
  maximum_per_round: 0
  maximum_per_command: 100

pot:
  base_amount: "1000.00"
  payout_percentage: "100.00"
  donations_enabled: true
  minimum_donation: "1.00"

prizes:
  shares: ["100.00"]
  allow_same_player_multiple_prizes: false

anti_snipe:
  enabled: true
  trigger_remaining: 30s
  extension: 15s
  maximum_total_extension: 5m

broadcasts:
  enabled: true
  remaining_times: [1h, 30m, 10m, 5m, 1m, 30s, 10s]

payouts:
  automatic_on_join: true
  claim_command_enabled: true

history:
  page_size: 10
  leaderboard_size: 10
```

`maximum_per_player: 0` and `maximum_per_round: 0` mean unlimited. Prize shares must be positive and total exactly `100`. Money accepts at most two decimal places.

## Draw fairness

Entries are sorted by UUID and assigned ticket ranges. The feature uses a random 256-bit seed, publishes its SHA-256 commitment before the draw, and later stores the seed reveal and an entry digest. SHA-256 rejection sampling avoids modulo bias. Prize rounding remainder is assigned to the first actual winner so the full payout is conserved.

## PlaceholderAPI

```text
%lottery_available%
%lottery_lottery_key%
%lottery_status%
%lottery_round_id%
%lottery_pot%
%lottery_ticket_price%
%lottery_tickets_sold%
%lottery_participants%
%lottery_time_remaining%
%lottery_next_draw_epoch%
%lottery_seed_commitment%
%lottery_player_tickets%
%lottery_player_odds%
%lottery_player_pending_payout%
%lottery_player_total_won%
%lottery_player_total_donated%
```

Placeholder reads use cached snapshots only and perform no database access.
