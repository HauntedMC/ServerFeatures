package nl.hauntedmc.serverfeatures.features.economy.config;

import nl.hauntedmc.serverfeatures.api.economy.EconomyScope;
import nl.hauntedmc.serverfeatures.api.economy.EconomyScopeType;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.serverfeatures.framework.config.FeatureConfigHandler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses external configuration into the immutable {@link EconomySettings} model.
 *
 * <p>Keeping parsing here makes the settings record safe to use as an in-memory domain value:
 * it contains no configuration-tree traversal or defaulting logic.</p>
 */
final class EconomySettingsLoader {
    private EconomySettingsLoader() {
    }

    static EconomySettings load(FeatureConfigHandler config, String globalServerName) {
        String networkKey = EconomySettings.key(EconomySettings.text(config.node(), "network_key", "hauntedmc"), "network_key");
        String configuredServer = EconomySettings.firstNonBlank(
                EconomySettings.text(config.node(), "local_key", ""),
                EconomySettings.text(config.node(), "gamemode_key", ""),
                EconomySettings.text(config.node(), "server_key", "$server")
        );
        String serverKey = "$server".equalsIgnoreCase(configuredServer)
                ? EconomySettings.key(globalServerName, "global server_name")
                : EconomySettings.key(configuredServer, "server_key");
        EconomySettings.Vault vault = new EconomySettings.Vault(
                EconomySettings.bool(config.node(), "vault.enabled", true),
                EconomySettings.normalizeCurrencyId(EconomySettings.text(config.node(), "vault.primary_currency", "money")),
                EconomySettings.enumValue(EconomySettings.VaultConflictPolicy.class,
                        EconomySettings.text(config.node(), "vault.conflict_policy", "FAIL"), "vault.conflict_policy")
        );
        EconomySettings.Messaging messaging = new EconomySettings.Messaging(
                EconomySettings.bool(config.node(), "messaging.enabled", true),
                EconomySettings.text(config.node(), "messaging.connection", "hauntedmc"),
                EconomySettings.text(config.node(), "messaging.channel", "serverfeatures.economy.balance")
        );
        EconomySettings.Cache cache = new EconomySettings.Cache(EconomySettings.duration(config.node(),
                "cache.authoritative_refresh_interval", "10s", Duration.ofSeconds(1), Duration.ofMinutes(5)));

        Map<String, EconomySettings.Currency> currencies = new LinkedHashMap<>();
        for (Map.Entry<String, ConfigNode> entry : config.node("currencies").children().entrySet()) {
            String id = EconomySettings.normalizeCurrencyId(entry.getKey());
            ConfigNode node = entry.getValue();
            if (!EconomySettings.bool(node, "enabled", true)) continue;
            EconomyScopeType scopeType = EconomySettings.scopeType(EconomySettings.text(node, "scope.type", "SERVER"),
                    "currencies." + id + ".scope.type");
            String scopeKey = scopeKey(node, id, networkKey, serverKey, scopeType);
            int digits = EconomySettings.integer(node, "display.fractional_digits", 2, 0, 8);
            RoundingMode rounding = EconomySettings.enumValue(RoundingMode.class,
                    EconomySettings.text(node, "balances.rounding", "HALF_UP"), "currencies." + id + ".balances.rounding");
            EconomySettings.Balances balances = balances(node, id, digits, rounding);
            EconomySettings.Commands commands = new EconomySettings.Commands(
                    EconomySettings.commandLabel(EconomySettings.text(node, "commands.root", id)), EconomySettings.aliases(node.getAt("commands.aliases")),
                    EconomySettings.bool(node, "commands.balance", true), EconomySettings.bool(node, "commands.balance_others", true),
                    EconomySettings.bool(node, "commands.pay", true), EconomySettings.bool(node, "commands.paytoggle", true),
                    EconomySettings.bool(node, "commands.history", true), EconomySettings.bool(node, "commands.top", false));
            EconomySettings.Payments payments = payments(node, id, digits, rounding);
            validateCurrency(id, node, commands, payments);
            EconomySettings.Currency currency = new EconomySettings.Currency(id, new EconomyScope(scopeType, scopeKey),
                    new EconomySettings.Display(EconomySettings.text(node, "display.singular", id),
                            EconomySettings.text(node, "display.plural", id), EconomySettings.text(node, "display.symbol", ""),
                            EconomySettings.text(node, "display.format", "{symbol}{amount}"), digits,
                            EconomySettings.bool(node, "display.grouping", true)), balances, commands, payments);
            if (currencies.putIfAbsent(id, currency) != null) throw new IllegalArgumentException("Duplicate normalized currency id: " + id);
        }
        return new EconomySettings(networkKey, serverKey, EconomySettings.text(config.node(), "database.connection", "system_data_rw"),
                vault, messaging, cache, currencies);
    }

    private static String scopeKey(ConfigNode node, String id, String networkKey, String serverKey, EconomyScopeType type) {
        String key = switch (type) {
            case SERVER -> networkKey + "/server/" + EconomySettings.localScopeKey(node, id, serverKey);
            case GROUP -> networkKey + "/group/" + EconomySettings.key(EconomySettings.text(node, "scope.group_key", ""), "currencies." + id + ".scope.group_key");
            case GLOBAL -> networkKey + "/global";
        };
        if (key.length() > 128) throw new IllegalArgumentException("Resolved scope key for currency " + id + " exceeds 128 characters");
        return key;
    }

    private static EconomySettings.Balances balances(ConfigNode node, String id, int digits, RoundingMode rounding) {
        BigDecimal starting = EconomySettings.amount(node, "balances.starting", "0", digits, rounding);
        BigDecimal minimum = EconomySettings.amount(node, "balances.minimum", "0", digits, rounding);
        BigDecimal maximum = EconomySettings.amount(node, "balances.maximum", "999999999999999999999999999999.99999999", digits, rounding);
        boolean allowNegative = EconomySettings.bool(node, "balances.allow_negative", false);
        if (!allowNegative && minimum.signum() < 0) throw new IllegalArgumentException("Currency " + id + " has a negative minimum while allow_negative is false");
        if (minimum.compareTo(maximum) > 0) throw new IllegalArgumentException("Currency " + id + " minimum balance exceeds maximum balance");
        if (starting.compareTo(minimum) < 0 || starting.compareTo(maximum) > 0) throw new IllegalArgumentException("Currency " + id + " starting balance is outside configured bounds");
        return new EconomySettings.Balances(starting, minimum, maximum, allowNegative, rounding);
    }

    private static EconomySettings.Payments payments(ConfigNode node, String id, int digits, RoundingMode rounding) {
        String defaultMinimum = BigDecimal.ONE.movePointLeft(digits).setScale(digits).toPlainString();
        return new EconomySettings.Payments(EconomySettings.bool(node, "payments.default_enabled", true),
                EconomySettings.positiveAmount(node, "payments.minimum", digits, rounding, defaultMinimum),
                EconomySettings.nonNegativeAmount(node, "payments.maximum", digits, rounding, "0"),
                EconomySettings.nonNegativeAmount(node, "payments.confirmation_threshold", digits, rounding, "0"),
                EconomySettings.nonNegativeAmount(node, "payments.daily_send_limit", digits, rounding, "0"),
                EconomySettings.nonNegativeAmount(node, "payments.daily_receive_limit", digits, rounding, "0"),
                EconomySettings.duration(node, "payments.cooldown", "1s", Duration.ZERO, Duration.ofHours(1)));
    }

    private static void validateCurrency(String id, ConfigNode node, EconomySettings.Commands commands, EconomySettings.Payments payments) {
        ConfigNode offlineRecipient = node.getAt("payments.allow_offline_recipient");
        if (!offlineRecipient.isNull() && !offlineRecipient.as(Boolean.class, true))
            throw new IllegalArgumentException("Currency " + id + " cannot disable offline recipients: known players must remain payable across the network");
        if (!commands.pay() && commands.paytoggle()) throw new IllegalArgumentException("Currency " + id + " enables paytoggle while pay is disabled");
        if (payments.maximum().signum() > 0 && payments.maximum().compareTo(payments.minimum()) < 0)
            throw new IllegalArgumentException("Currency " + id + " payment maximum is below the minimum");
        if (payments.dailySendLimit().signum() > 0 && payments.dailySendLimit().compareTo(payments.minimum()) < 0)
            throw new IllegalArgumentException("Currency " + id + " daily send limit is below the minimum payment");
        if (payments.dailyReceiveLimit().signum() > 0 && payments.dailyReceiveLimit().compareTo(payments.minimum()) < 0)
            throw new IllegalArgumentException("Currency " + id + " daily receive limit is below the minimum payment");
    }
}
