package nl.hauntedmc.serverfeatures.features.economy.config;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;

import java.util.List;

/** Factory for Economy's shipped configuration and localization defaults. */
public final class EconomyDefaults {
    private EconomyDefaults() {
    }

    public static ConfigMap config() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("network_key", "hauntedmc");
        defaults.put("server_key", "$server");
        defaults.put("database.connection", "system_data_rw");
        defaults.put("messaging.enabled", true);
        defaults.put("messaging.connection", "hauntedmc");
        defaults.put("messaging.channel", "serverfeatures.economy.balance");
        defaults.put("cache.authoritative_refresh_interval", "10s");
        defaults.put("execution.workers", 4);
        defaults.put("execution.queue_capacity", 256);
        defaults.put("execution.synchronous_timeout", "2s");
        defaults.put("execution.shutdown_drain", "5s");
        defaults.put("vault.enabled", true);
        defaults.put("vault.primary_currency", "money");
        defaults.put("vault.conflict_policy", "FAIL");
        defaults.put("currencies.money.enabled", true);
        defaults.put("currencies.money.scope.type", "SERVER");
        defaults.put("currencies.money.display.singular", "coin");
        defaults.put("currencies.money.display.plural", "coins");
        defaults.put("currencies.money.display.symbol", "$");
        defaults.put("currencies.money.display.format", "{symbol}{amount}");
        defaults.put("currencies.money.display.fractional_digits", 2);
        defaults.put("currencies.money.display.grouping", true);
        defaults.put("currencies.money.balances.starting", "0.00");
        defaults.put("currencies.money.balances.minimum", "0.00");
        defaults.put("currencies.money.balances.maximum", "999999999999.99");
        defaults.put("currencies.money.balances.allow_negative", false);
        defaults.put("currencies.money.balances.rounding", "HALF_UP");
        defaults.put("currencies.money.commands.root", "money");
        defaults.put("currencies.money.commands.aliases", List.of("balance", "bal"));
        defaults.put("currencies.money.commands.balance", true);
        defaults.put("currencies.money.commands.balance_others", true);
        defaults.put("currencies.money.commands.pay", true);
        defaults.put("currencies.money.commands.paytoggle", true);
        defaults.put("currencies.money.commands.history", true);
        defaults.put("currencies.money.commands.top", false);
        defaults.put("currencies.money.payments.default_enabled", true);
        defaults.put("currencies.money.payments.minimum", "0.01");
        defaults.put("currencies.money.payments.maximum", "1000000.00");
        defaults.put("currencies.money.payments.confirmation_threshold", "100000.00");
        defaults.put("currencies.money.payments.daily_send_limit", "0.00");
        defaults.put("currencies.money.payments.daily_receive_limit", "0.00");
        defaults.put("currencies.money.payments.cooldown", "1s");
        return defaults;
    }

    public static MessageMap messages() {
        MessageMap messages = new MessageMap();
        messages.add("economy.player_only", "<red>Dit commando kan alleen door een speler worden gebruikt.</red>");
        messages.add("economy.error", "<red>De economieactie is mislukt: {reason}</red>");
        messages.add("economy.invalid_amount", "<red>Ongeldig bedrag: {reason}</red>");
        messages.add("economy.balance.self", "<gray>Je saldo:</gray> <gold>{balance}</gold>");
        messages.add("economy.balance.other", "<gray>Saldo van {player}:</gray> <gold>{balance}</gold>");
        messages.add("economy.pay.cooldown", "<yellow>Wacht nog {seconds} seconde(n) voor een nieuwe betaling.</yellow>");
        messages.add("economy.pay.confirm", "<yellow>Bevestig de betaling van {amount} aan {player} met <white>{command}</white>.</yellow>");
        messages.add("economy.pay.no_confirmation", "<yellow>Er staat geen geldige betaling klaar om te bevestigen.</yellow>");
        messages.add("economy.pay.failed", "<red>De betaling is mislukt: {reason}</red>");
        messages.add("economy.pay.sent", "<green>Je betaalde {amount} aan {player}.</green> <gray>Nieuw saldo: {balance}</gray>");
        messages.add("economy.pay.received", "<green>Je ontving {amount} van {player}.</green> <gray>Nieuw saldo: {balance}</gray>");
        messages.add("economy.paytoggle.enabled", "<green>Je accepteert betalingen van andere spelers.</green>");
        messages.add("economy.paytoggle.disabled", "<yellow>Je accepteert geen betalingen van andere spelers.</yellow>");
        messages.add("economy.history.header", "<gold><bold>Transactiegeschiedenis</bold></gold> <gray>pagina {page}</gray>");
        messages.add("economy.history.empty", "<gray>Geen transacties gevonden.</gray>");
        messages.add("economy.history.entry", "<gray>{type}</gray> <white>{amount}</white> <dark_gray>→ {balance} · {operation}</dark_gray>");
        messages.add("economy.top.header", "<gold><bold>Ranglijst</bold></gold> <gray>pagina {page}</gray>");
        messages.add("economy.top.empty", "<gray>Geen spelers gevonden op deze pagina.</gray>");
        messages.add("economy.top.entry", "<aqua>#{rank}</aqua> <white>{player}</white> <gray>· {balance}</gray>");
        messages.add("economy.help.header", "<gold><bold>Economy-hulp</bold></gold> <gray>{command}</gray>");
        messages.add("economy.help.entry", "<white>{command} {usage}</white> <gray>— {description}</gray>");
        messages.add("economy.admin.status", "<gold>Economy</gold> <gray>· server {server} · {currencies} currencies · Vault {vault} · messaging {messaging}</gray>");
        messages.add("economy.admin.currency", "<white>{currency}</white> <gray>· {scope} · {scope_key} · {command}</gray>");
        messages.add("economy.admin.definition.empty", "<gray>Geen gedeelde currency-definities gevonden voor dit netwerk.</gray>");
        messages.add("economy.admin.definition.list", "<white>{currency}</white> <gray>· {type} · {scope} · {state}</gray>");
        messages.add("economy.admin.definition.missing", "<red>Geen gedeelde definitie gevonden voor {currency} in {scope}.</red>");
        messages.add("economy.admin.definition.legacy", "<yellow>{currency} in {scope} is een legacy-definitie zonder import-payload. Start eerst een server met de bekende goede config.</yellow>");
        messages.add("economy.admin.definition.detail", "<white>{currency}</white> <gray>· {type} · {scope} · {digits} decimalen · start {starting} · grenzen {minimum}..{maximum} · negatief {negative} · {rounding} · betalingen standaard {payments} · min/max {payment_minimum}/{payment_maximum} · bevestiging {confirmation} · daglimieten uit/in {daily_send}/{daily_receive} · cooldown {cooldown}</gray>");
        messages.add("economy.admin.definition.import_preview", "<yellow>Import {currency} ({scope}): {message}</yellow>");
        messages.add("economy.admin.definition.imported", "<green>Import {currency} ({scope}): {message}</green>");
        messages.add("economy.admin.balance", "<white>{player}</white> <gray>· {currency} · {scope} ·</gray> <gold>{balance}</gold>");
        messages.add("economy.admin.account", "<white>{player}</white> <gray>· {currency} · {scope} · saldo</gray> <gold>{balance}</gold> <gray>· betalingen {payments} · status {status}</gray>");
        messages.add("economy.admin.help.header", "<gold><bold>Economy-beheer</bold></gold>");
        messages.add("economy.admin.help.entry", "<white>/economy {usage}</white> <gray>— {description}</gray>");
        messages.add("economy.admin.reason_required", "<red>Een reden is verplicht.</red>");
        messages.add("economy.admin.changed", "<green>Saldo van {player} ({currency}) aangepast naar {balance}.</green> <gray>Transactie {operation}</gray>");
        messages.add("economy.admin.payments", "<green>Betalingen voor {player} staan nu {state}.</green>");
        messages.add("economy.admin.frozen", "<yellow>Account {player}/{currency} is bevroren.</yellow>");
        messages.add("economy.admin.unfrozen", "<green>Account {player}/{currency} is vrijgegeven.</green>");
        messages.add("economy.admin.verify", "<gray>Status {health} · accounts {accounts} · transacties {transactions} · ongeldige saldi {invalid} · ongeldige regels {invalid_entries} · ongeldige transacties {invalid_transactions} · losse instellingen {orphan_settings} · losse regels {orphan_entries} · identiteitsfouten {identity_mismatches} · verkeerde accountregels {entry_account_mismatches} · accounts zonder journaal {accounts_without_entries} · lege transacties {empty_transactions} · saldo/journaalfouten {balance_journal_mismatches} · ketenfouten {continuity_errors}</gray>");
        return messages;
    }
}
