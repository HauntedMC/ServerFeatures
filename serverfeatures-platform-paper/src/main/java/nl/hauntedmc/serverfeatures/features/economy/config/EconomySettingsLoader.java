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
        String networkKey = EconomyConfigValues.key(EconomyConfigValues.text(config.node(), "network_key", "hauntedmc"), "network_key");
        String configuredServer = EconomyConfigValues.firstNonBlank(
                EconomyConfigValues.text(config.node(), "local_key", ""),
                EconomyConfigValues.text(config.node(), "gamemode_key", ""),
                EconomyConfigValues.text(config.node(), "server_key", "$server")
        );
        String serverKey = "$server".equalsIgnoreCase(configuredServer)
                ? EconomyConfigValues.key(globalServerName, "global server_name")
                : EconomyConfigValues.key(configuredServer, "server_key");
        EconomySettings.Vault vault = new EconomySettings.Vault(
                EconomyConfigValues.bool(config.node(), "vault.enabled", true),
                EconomySettings.normalizeCurrencyId(EconomyConfigValues.text(config.node(), "vault.primary_currency", "money")),
                EconomyConfigValues.enumValue(EconomySettings.VaultConflictPolicy.class,
                        EconomyConfigValues.text(config.node(), "vault.conflict_policy", "FAIL"), "vault.conflict_policy")
        );
        EconomySettings.Messaging messaging = new EconomySettings.Messaging(
                EconomyConfigValues.bool(config.node(), "messaging.enabled", true),
                EconomyConfigValues.text(config.node(), "messaging.connection", "hauntedmc"),
                EconomyConfigValues.text(config.node(), "messaging.channel", "serverfeatures.economy.balance")
        );
        EconomySettings.Cache cache = new EconomySettings.Cache(EconomyConfigValues.duration(config.node(),
                "cache.authoritative_refresh_interval", "10s", Duration.ofSeconds(1), Duration.ofMinutes(5)));
        EconomySettings.Execution execution = new EconomySettings.Execution(
                EconomyConfigValues.integer(config.node(), "execution.workers", 4, 1, 64),
                EconomyConfigValues.integer(config.node(), "execution.queue_capacity", 256, 1, 100_000),
                EconomyConfigValues.duration(config.node(), "execution.synchronous_timeout", "2s",
                        Duration.ofMillis(1), Duration.ofSeconds(30)),
                EconomyConfigValues.duration(config.node(), "execution.shutdown_drain", "5s",
                        Duration.ZERO, Duration.ofSeconds(30))
        );

        Map<String, EconomySettings.Currency> currencies = new LinkedHashMap<>();
        for (Map.Entry<String, ConfigNode> entry : config.node("currencies").children().entrySet()) {
            String id = EconomySettings.normalizeCurrencyId(entry.getKey());
            ConfigNode node = entry.getValue();
            if (!EconomyConfigValues.bool(node, "enabled", true)) continue;
            // Defaults are injected into existing configs. A legacy currency may therefore gain
            // a new default definition branch beside its real legacy values; retain the latter
            // until an administrator explicitly migrates the currency to definition.*.
            ConfigNode definition = hasLegacyDefinition(node) ? ConfigNode.ofRaw(null, "legacy-definition")
                    : node.getAt("definition");
            EconomyScopeType scopeType = EconomyConfigValues.scopeType(definitionText(definition, node, "scope.type", "SERVER"),
                    "currencies." + id + ".definition.scope.type");
            String scopeKey = scopeKey(node, definition, id, networkKey, serverKey, scopeType);
            int digits = definitionInteger(definition, node, "fractional_digits", "display.fractional_digits", 2, 0, 8);
            RoundingMode rounding = EconomyConfigValues.enumValue(RoundingMode.class,
                    definitionText(definition, node, "balances.rounding", "HALF_UP"), "currencies." + id + ".definition.balances.rounding");
            EconomySettings.Balances balances = balances(definition.isNull() ? node : definition, id, digits, rounding);
            EconomySettings.Commands commands = new EconomySettings.Commands(
                    EconomyConfigValues.commandLabel(EconomyConfigValues.text(node, "commands.root", id)), EconomyConfigValues.aliases(node.getAt("commands.aliases")),
                    EconomyConfigValues.bool(node, "commands.balance", true), EconomyConfigValues.bool(node, "commands.balance_others", true),
                    EconomyConfigValues.bool(node, "commands.pay", true), EconomyConfigValues.bool(node, "commands.paytoggle", true),
                    EconomyConfigValues.bool(node, "commands.history", true), EconomyConfigValues.bool(node, "commands.top", false));
            EconomySettings.Payments payments = payments(node, id, digits, rounding);
            validateCurrency(id, node, commands, payments);
            EconomySettings.Currency currency = new EconomySettings.Currency(id, new EconomyScope(scopeType, scopeKey),
                    new EconomySettings.Display(EconomyConfigValues.text(node, "display.singular", id),
                            EconomyConfigValues.text(node, "display.plural", id), EconomyConfigValues.text(node, "display.symbol", ""),
                            EconomyConfigValues.text(node, "display.format", "{symbol}{amount}"), digits,
                            EconomyConfigValues.bool(node, "display.grouping", true)), balances, commands, payments);
            if (currencies.putIfAbsent(id, currency) != null) throw new IllegalArgumentException("Duplicate normalized currency id: " + id);
        }
        return new EconomySettings(networkKey, serverKey, vault, messaging, cache, execution, currencies);
    }

    /** Reads the new durable definition section while accepting pre-definition configs once. */
    private static String definitionText(ConfigNode definition, ConfigNode legacy, String path, String fallback) {
        return definition.isNull() ? EconomyConfigValues.text(legacy, path, fallback)
                : EconomyConfigValues.text(definition, path, fallback);
    }

    private static int definitionInteger(ConfigNode definition, ConfigNode legacy, String path, String legacyPath,
                                         int fallback, int minimum, int maximum) {
        return definition.isNull() ? EconomyConfigValues.integer(legacy, legacyPath, fallback, minimum, maximum)
                : EconomyConfigValues.integer(definition, path, fallback, minimum, maximum);
    }

    private static boolean hasLegacyDefinition(ConfigNode currency) {
        return !currency.getAt("scope").isNull()
                || !currency.getAt("balances").isNull()
                || !currency.getAt("display.fractional_digits").isNull();
    }

    private static String scopeKey(ConfigNode node, ConfigNode definition, String id, String networkKey,
                                   String serverKey, EconomyScopeType type) {
        String key = switch (type) {
            case SERVER -> networkKey + "/server/" + EconomyConfigValues.localScopeKey(
                    definition.isNull() ? node : definition, id, serverKey);
            case GROUP -> networkKey + "/group/" + EconomyConfigValues.key(definitionText(definition, node,
                    "scope.group_key", ""), "currencies." + id + ".definition.scope.group_key");
            case GLOBAL -> networkKey + "/global";
        };
        if (key.length() > 128) throw new IllegalArgumentException("Resolved scope key for currency " + id + " exceeds 128 characters");
        return key;
    }

    private static EconomySettings.Balances balances(ConfigNode node, String id, int digits, RoundingMode rounding) {
        BigDecimal starting = EconomyConfigValues.amount(node, "balances.starting", "0", digits, rounding);
        BigDecimal minimum = EconomyConfigValues.amount(node, "balances.minimum", "0", digits, rounding);
        BigDecimal maximum = EconomyConfigValues.amount(node, "balances.maximum", "999999999999999999999999999999.99999999", digits, rounding);
        boolean allowNegative = EconomyConfigValues.bool(node, "balances.allow_negative", false);
        if (!allowNegative && minimum.signum() < 0) throw new IllegalArgumentException("Currency " + id + " has a negative minimum while allow_negative is false");
        if (minimum.compareTo(maximum) > 0) throw new IllegalArgumentException("Currency " + id + " minimum balance exceeds maximum balance");
        if (starting.compareTo(minimum) < 0 || starting.compareTo(maximum) > 0) throw new IllegalArgumentException("Currency " + id + " starting balance is outside configured bounds");
        return new EconomySettings.Balances(starting, minimum, maximum, allowNegative, rounding);
    }

    private static EconomySettings.Payments payments(ConfigNode node, String id, int digits, RoundingMode rounding) {
        String defaultMinimum = BigDecimal.ONE.movePointLeft(digits).setScale(digits).toPlainString();
        return new EconomySettings.Payments(EconomyConfigValues.bool(node, "payments.default_enabled", true),
                EconomyConfigValues.positiveAmount(node, "payments.minimum", digits, rounding, defaultMinimum),
                EconomyConfigValues.nonNegativeAmount(node, "payments.maximum", digits, rounding, "0"),
                EconomyConfigValues.nonNegativeAmount(node, "payments.confirmation_threshold", digits, rounding, "0"),
                EconomyConfigValues.nonNegativeAmount(node, "payments.daily_send_limit", digits, rounding, "0"),
                EconomyConfigValues.nonNegativeAmount(node, "payments.daily_receive_limit", digits, rounding, "0"),
                EconomyConfigValues.duration(node, "payments.cooldown", "1s", Duration.ZERO, Duration.ofHours(1)));
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
