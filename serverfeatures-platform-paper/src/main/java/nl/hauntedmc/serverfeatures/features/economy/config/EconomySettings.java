package nl.hauntedmc.serverfeatures.features.economy.config;

import nl.hauntedmc.serverfeatures.api.economy.EconomyScope;
import nl.hauntedmc.serverfeatures.api.economy.EconomyScopeType;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.serverfeatures.framework.config.FeatureConfigHandler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Strict immutable configuration for the Economy feature. */
public record EconomySettings(
        String networkKey,
        String serverKey,
        String databaseConnection,
        Vault vault,
        Messaging messaging,
        Cache cache,
        Map<String, Currency> currencies
) {

    private static final String KEY_PATTERN = "[a-z0-9][a-z0-9_.-]{0,63}";

    public EconomySettings {
        networkKey = key(networkKey, "network_key");
        serverKey = key(serverKey, "server_key");
        databaseConnection = requireText(databaseConnection, "database.connection");
        Objects.requireNonNull(vault, "vault");
        Objects.requireNonNull(messaging, "messaging");
        Objects.requireNonNull(cache, "cache");
        currencies = Collections.unmodifiableMap(new LinkedHashMap<>(currencies));
        if (currencies.isEmpty()) {
            throw new IllegalArgumentException("At least one enabled currency is required");
        }
        if (vault.enabled() && !currencies.containsKey(vault.primaryCurrency())) {
            throw new IllegalArgumentException("vault.primary_currency must reference an enabled currency");
        }
        validateCommandLabels(currencies.values());
    }

    public static EconomySettings load(FeatureConfigHandler config, String globalServerName) {
        String networkKey = key(text(config.node(), "network_key", "hauntedmc"), "network_key");
        String configuredServer = firstNonBlank(
                text(config.node(), "local_key", ""),
                text(config.node(), "gamemode_key", ""),
                text(config.node(), "server_key", "$server")
        );
        String serverKey = "$server".equalsIgnoreCase(configuredServer)
                ? key(globalServerName, "global server_name")
                : key(configuredServer, "server_key");
        String connection = text(config.node(), "database.connection", "system_data_rw");

        Vault vault = new Vault(
                bool(config.node(), "vault.enabled", true),
                normalizeCurrencyId(text(config.node(), "vault.primary_currency", "money")),
                enumValue(VaultConflictPolicy.class, text(config.node(), "vault.conflict_policy", "FAIL"),
                        "vault.conflict_policy")
        );
        Messaging messaging = new Messaging(
                bool(config.node(), "messaging.enabled", true),
                text(config.node(), "messaging.connection", "hauntedmc"),
                text(config.node(), "messaging.channel", "serverfeatures.economy.balance")
        );
        Cache cache = new Cache(duration(
                config.node(),
                "cache.authoritative_refresh_interval",
                "10s",
                Duration.ofSeconds(1),
                Duration.ofMinutes(5)
        ));

        ConfigNode currenciesNode = config.node("currencies");
        Map<String, Currency> currencies = new LinkedHashMap<>();
        for (Map.Entry<String, ConfigNode> entry : currenciesNode.children().entrySet()) {
            String id = normalizeCurrencyId(entry.getKey());
            ConfigNode node = entry.getValue();
            if (!bool(node, "enabled", true)) {
                continue;
            }
            EconomyScopeType scopeType = scopeType(
                    text(node, "scope.type", "SERVER"),
                    "currencies." + id + ".scope.type"
            );
            String scopeKey = switch (scopeType) {
                case SERVER -> networkKey + "/server/" + localScopeKey(node, id, serverKey);
                case GROUP -> networkKey + "/group/" + key(
                        text(node, "scope.group_key", ""),
                        "currencies." + id + ".scope.group_key"
                );
                case GLOBAL -> networkKey + "/global";
            };
            if (scopeKey.length() > 128) {
                throw new IllegalArgumentException(
                        "Resolved scope key for currency " + id + " exceeds 128 characters"
                );
            }

            int fractionalDigits = integer(node, "display.fractional_digits", 2, 0, 8);
            RoundingMode roundingMode = enumValue(
                    RoundingMode.class,
                    text(node, "balances.rounding", "HALF_UP"),
                    "currencies." + id + ".balances.rounding"
            );
            BigDecimal starting = amount(node, "balances.starting", "0", fractionalDigits, roundingMode);
            BigDecimal minimum = amount(node, "balances.minimum", "0", fractionalDigits, roundingMode);
            BigDecimal maximum = amount(
                    node,
                    "balances.maximum",
                    "999999999999999999999999999999.99999999",
                    fractionalDigits,
                    roundingMode
            );
            boolean allowNegative = bool(node, "balances.allow_negative", false);
            if (!allowNegative && minimum.signum() < 0) {
                throw new IllegalArgumentException("Currency " + id + " has a negative minimum while allow_negative is false");
            }
            if (minimum.compareTo(maximum) > 0) {
                throw new IllegalArgumentException("Currency " + id + " minimum balance exceeds maximum balance");
            }
            if (starting.compareTo(minimum) < 0 || starting.compareTo(maximum) > 0) {
                throw new IllegalArgumentException("Currency " + id + " starting balance is outside configured bounds");
            }

            Commands commands = new Commands(
                    commandLabel(text(node, "commands.root", id)),
                    aliases(node.getAt("commands.aliases")),
                    bool(node, "commands.balance", true),
                    bool(node, "commands.balance_others", true),
                    bool(node, "commands.pay", true),
                    bool(node, "commands.paytoggle", true),
                    bool(node, "commands.history", true),
                    bool(node, "commands.top", false)
            );
            ConfigNode offlineRecipientSetting = node.getAt("payments.allow_offline_recipient");
            if (!offlineRecipientSetting.isNull()
                    && !offlineRecipientSetting.as(Boolean.class, true)) {
                throw new IllegalArgumentException(
                        "Currency " + id + " cannot disable offline recipients: "
                                + "known players must remain payable across the network"
                );
            }
            String minimumPaymentDefault = BigDecimal.ONE
                    .movePointLeft(fractionalDigits)
                    .setScale(fractionalDigits)
                    .toPlainString();
            Payments payments = new Payments(
                    bool(node, "payments.default_enabled", true),
                    positiveAmount(node, "payments.minimum", fractionalDigits, roundingMode, minimumPaymentDefault),
                    nonNegativeAmount(node, "payments.maximum", fractionalDigits, roundingMode, "0"),
                    nonNegativeAmount(node, "payments.confirmation_threshold", fractionalDigits, roundingMode, "0"),
                    nonNegativeAmount(node, "payments.daily_send_limit", fractionalDigits, roundingMode, "0"),
                    nonNegativeAmount(node, "payments.daily_receive_limit", fractionalDigits, roundingMode, "0"),
                    duration(node, "payments.cooldown", "1s", Duration.ZERO, Duration.ofHours(1))
            );
            if (!commands.pay() && commands.paytoggle()) {
                throw new IllegalArgumentException("Currency " + id + " enables paytoggle while pay is disabled");
            }
            if (payments.maximum().signum() > 0 && payments.maximum().compareTo(payments.minimum()) < 0) {
                throw new IllegalArgumentException("Currency " + id + " payment maximum is below the minimum");
            }
            if (payments.dailySendLimit().signum() > 0
                    && payments.dailySendLimit().compareTo(payments.minimum()) < 0) {
                throw new IllegalArgumentException("Currency " + id + " daily send limit is below the minimum payment");
            }
            if (payments.dailyReceiveLimit().signum() > 0
                    && payments.dailyReceiveLimit().compareTo(payments.minimum()) < 0) {
                throw new IllegalArgumentException(
                        "Currency " + id + " daily receive limit is below the minimum payment"
                );
            }

            Currency currency = new Currency(
                    id,
                    new EconomyScope(scopeType, scopeKey),
                    new Display(
                            text(node, "display.singular", id),
                            text(node, "display.plural", id),
                            text(node, "display.symbol", ""),
                            text(node, "display.format", "{symbol}{amount}"),
                            fractionalDigits,
                            bool(node, "display.grouping", true)
                    ),
                    new Balances(starting, minimum, maximum, allowNegative, roundingMode),
                    commands,
                    payments
            );
            if (currencies.putIfAbsent(id, currency) != null) {
                throw new IllegalArgumentException("Duplicate normalized currency id: " + id);
            }
        }
        return new EconomySettings(networkKey, serverKey, connection, vault, messaging, cache, currencies);
    }

    public Currency requireCurrency(String id) {
        Currency currency = currencies.get(normalizeCurrencyId(id));
        if (currency == null) {
            throw new IllegalArgumentException("Unknown currency: " + id);
        }
        return currency;
    }

    public record Vault(boolean enabled, String primaryCurrency, VaultConflictPolicy conflictPolicy) {
        public Vault {
            primaryCurrency = normalizeCurrencyId(primaryCurrency);
            Objects.requireNonNull(conflictPolicy, "conflictPolicy");
        }
    }

    public record Messaging(boolean enabled, String connection, String channel) {
        public Messaging {
            connection = requireText(connection, "messaging.connection");
            channel = requireText(channel, "messaging.channel");
        }
    }

    public record Cache(Duration authoritativeRefreshInterval) {
        public Cache {
            Objects.requireNonNull(authoritativeRefreshInterval, "authoritativeRefreshInterval");
            if (authoritativeRefreshInterval.isZero() || authoritativeRefreshInterval.isNegative()) {
                throw new IllegalArgumentException("cache.authoritative_refresh_interval must be positive");
            }
        }
    }

    public record Currency(
            String id,
            EconomyScope scope,
            Display display,
            Balances balances,
            Commands commands,
            Payments payments
    ) {
        public Currency {
            id = normalizeCurrencyId(id);
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(display, "display");
            Objects.requireNonNull(balances, "balances");
            Objects.requireNonNull(commands, "commands");
            Objects.requireNonNull(payments, "payments");
        }
    }

    public record Display(
            String singular,
            String plural,
            String symbol,
            String format,
            int fractionalDigits,
            boolean grouping
    ) {
        public Display {
            singular = requireText(singular, "display.singular");
            plural = requireText(plural, "display.plural");
            symbol = symbol == null ? "" : symbol;
            format = requireText(format, "display.format");
        }
    }

    public record Balances(
            BigDecimal starting,
            BigDecimal minimum,
            BigDecimal maximum,
            boolean allowNegative,
            RoundingMode rounding
    ) {
        public Balances {
            Objects.requireNonNull(starting, "starting");
            Objects.requireNonNull(minimum, "minimum");
            Objects.requireNonNull(maximum, "maximum");
            Objects.requireNonNull(rounding, "rounding");
        }
    }

    public record Commands(
            String root,
            List<String> aliases,
            boolean balance,
            boolean balanceOthers,
            boolean pay,
            boolean paytoggle,
            boolean history,
            boolean top
    ) {
        public Commands {
            root = commandLabel(root);
            aliases = List.copyOf(aliases);
        }
    }

    public record Payments(
            boolean defaultEnabled,
            BigDecimal minimum,
            BigDecimal maximum,
            BigDecimal confirmationThreshold,
            BigDecimal dailySendLimit,
            BigDecimal dailyReceiveLimit,
            Duration cooldown
    ) {
        public Payments {
            Objects.requireNonNull(minimum, "minimum");
            Objects.requireNonNull(maximum, "maximum");
            Objects.requireNonNull(confirmationThreshold, "confirmationThreshold");
            Objects.requireNonNull(dailySendLimit, "dailySendLimit");
            Objects.requireNonNull(dailyReceiveLimit, "dailyReceiveLimit");
            Objects.requireNonNull(cooldown, "cooldown");
        }
    }

    public enum VaultConflictPolicy {
        FAIL,
        SKIP,
        REPLACE
    }

    public static String normalizeCurrencyId(String value) {
        String id = requireText(value, "currency id").toLowerCase(Locale.ROOT);
        if (!id.matches(KEY_PATTERN)) {
            throw new IllegalArgumentException("Invalid currency id: " + value);
        }
        return id;
    }


    private static EconomyScopeType scopeType(String raw, String field) {
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("LOCAL") || normalized.equals("GAMEMODE")) {
            return EconomyScopeType.SERVER;
        }
        return enumValue(EconomyScopeType.class, normalized, field);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String localScopeKey(ConfigNode node, String currencyId, String fallback) {
        String configured = text(node, "scope.local_key", "");
        if (configured.isBlank()) {
            configured = text(node, "scope.gamemode_key", "");
        }
        if (configured.isBlank()) {
            configured = text(node, "scope.server_key", "");
        }
        return key(configured.isBlank() ? fallback : configured,
                "currencies." + currencyId + ".scope.local_key");
    }

    private static void validateCommandLabels(Iterable<Currency> currencies) {
        Set<String> labels = new LinkedHashSet<>();
        for (Currency currency : currencies) {
            List<String> candidateLabels = new ArrayList<>();
            candidateLabels.add(currency.commands().root());
            candidateLabels.addAll(currency.commands().aliases());
            for (String label : candidateLabels) {
                if (!labels.add(label.toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("Duplicate economy command label: " + label);
                }
            }
        }
    }

    private static List<String> aliases(ConfigNode node) {
        if (node.isNull()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String alias : node.listOf(String.class)) {
            String normalized = commandLabel(alias);
            if (!result.contains(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    private static String commandLabel(String value) {
        String label = requireText(value, "command label").toLowerCase(Locale.ROOT);
        if (!label.matches("[a-z0-9][a-z0-9_-]{0,31}")) {
            throw new IllegalArgumentException("Invalid command label: " + value);
        }
        return label;
    }

    private static String key(String value, String field) {
        String normalized = requireText(value, field).toLowerCase(Locale.ROOT).replace(' ', '-');
        if (!normalized.matches(KEY_PATTERN)) {
            throw new IllegalArgumentException(field + " must match " + KEY_PATTERN);
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String text(ConfigNode node, String path, String fallback) {
        String value = node.getAt(path).as(String.class, fallback);
        return value == null ? fallback : value.trim();
    }

    private static boolean bool(ConfigNode node, String path, boolean fallback) {
        return node.getAt(path).as(Boolean.class, fallback);
    }

    private static int integer(ConfigNode node, String path, int fallback, int minimum, int maximum) {
        int value = node.getAt(path).as(Integer.class, fallback);
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(path + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private static BigDecimal amount(
            ConfigNode node,
            String path,
            String fallback,
            int fractionalDigits,
            RoundingMode roundingMode
    ) {
        String value = text(node, path, fallback);
        try {
            BigDecimal amount = new BigDecimal(value);
            long integerDigits = (long) amount.precision() - amount.scale();
            if (amount.scale() > 8 || amount.signum() != 0 && integerDigits > 30L) {
                throw new IllegalArgumentException(path + " exceeds DECIMAL(38,8) storage precision");
            }
            if (amount.scale() > fractionalDigits) {
                amount = amount.setScale(fractionalDigits, roundingMode);
            }
            BigDecimal normalized = amount.setScale(fractionalDigits, roundingMode);
            if (normalized.precision() > 38) {
                throw new IllegalArgumentException(path + " exceeds DECIMAL(38,8) storage precision");
            }
            return normalized;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("Invalid amount at " + path + ": " + value, exception);
        }
    }

    private static BigDecimal positiveAmount(
            ConfigNode node,
            String path,
            int fractionalDigits,
            RoundingMode roundingMode,
            String fallback
    ) {
        BigDecimal value = amount(node, path, fallback, fractionalDigits, roundingMode);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(path + " must be positive");
        }
        return value;
    }

    private static BigDecimal nonNegativeAmount(
            ConfigNode node,
            String path,
            int fractionalDigits,
            RoundingMode roundingMode,
            String fallback
    ) {
        BigDecimal value = amount(node, path, fallback, fractionalDigits, roundingMode);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(path + " must not be negative");
        }
        return value;
    }

    private static Duration duration(
            ConfigNode node,
            String path,
            String fallback,
            Duration minimum,
            Duration maximum
    ) {
        String raw = text(node, path, fallback).toLowerCase(Locale.ROOT);
        long multiplier;
        String number;
        if (raw.endsWith("ms")) {
            multiplier = 1L;
            number = raw.substring(0, raw.length() - 2);
        } else if (raw.endsWith("s")) {
            multiplier = 1_000L;
            number = raw.substring(0, raw.length() - 1);
        } else if (raw.endsWith("m")) {
            multiplier = 60_000L;
            number = raw.substring(0, raw.length() - 1);
        } else if (raw.endsWith("h")) {
            multiplier = 3_600_000L;
            number = raw.substring(0, raw.length() - 1);
        } else {
            throw new IllegalArgumentException("Invalid duration at " + path + ": " + raw);
        }
        try {
            Duration duration = Duration.ofMillis(Math.multiplyExact(Long.parseLong(number.trim()), multiplier));
            if (duration.compareTo(minimum) < 0 || duration.compareTo(maximum) > 0) {
                throw new IllegalArgumentException(path + " is outside the allowed range");
            }
            return duration;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("Invalid duration at " + path + ": " + raw, exception);
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String raw, String field) {
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid " + field + ": " + raw, exception);
        }
    }
}
