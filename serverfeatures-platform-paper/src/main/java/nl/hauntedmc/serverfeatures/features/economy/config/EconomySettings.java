package nl.hauntedmc.serverfeatures.features.economy.config;

import nl.hauntedmc.serverfeatures.api.economy.EconomyScope;
import nl.hauntedmc.serverfeatures.framework.config.FeatureConfigHandler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, fully validated Economy configuration.
 *
 * <p>External tree traversal and primitive parsing belong to {@link EconomySettingsLoader};
 * constructors here enforce the invariants expected by the service and persistence layers.</p>
 */
public record EconomySettings(
        String networkKey,
        String serverKey,
        String databaseConnection,
        Vault vault,
        Messaging messaging,
        Cache cache,
        Execution execution,
        Map<String, Currency> currencies
) {
    public EconomySettings {
        networkKey = EconomyConfigValues.key(networkKey, "network_key");
        serverKey = EconomyConfigValues.key(serverKey, "server_key");
        databaseConnection = EconomyConfigValues.requireText(databaseConnection, "database.connection");
        Objects.requireNonNull(vault, "vault");
        Objects.requireNonNull(messaging, "messaging");
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(execution, "execution");
        currencies = Collections.unmodifiableMap(new LinkedHashMap<>(currencies));
        if (vault.enabled() && !currencies.containsKey(vault.primaryCurrency())) {
            throw new IllegalArgumentException("vault.primary_currency must reference an enabled currency");
        }
        EconomyConfigValues.validateCommandLabels(currencies.values());
    }

    /** Compatibility constructor for integrations/tests that use the default bounded executor settings. */
    public EconomySettings(String networkKey, String serverKey, String databaseConnection, Vault vault,
                           Messaging messaging, Cache cache, Map<String, Currency> currencies) {
        this(networkKey, serverKey, databaseConnection, vault, messaging, cache, Execution.defaults(), currencies);
    }

    /** Parses and validates Economy settings from the feature configuration. */
    public static EconomySettings load(FeatureConfigHandler config, String globalServerName) {
        return EconomySettingsLoader.load(config, globalServerName);
    }

    /** Returns a configured currency using normalized, case-insensitive identifiers. */
    public Currency requireCurrency(String id) {
        Currency currency = currencies.get(normalizeCurrencyId(id));
        if (currency == null) throw new IllegalArgumentException("Unknown currency: " + id);
        return currency;
    }

    /** Returns the effective runtime configuration after unavailable currencies were isolated. */
    public EconomySettings withCurrencies(Map<String, Currency> activeCurrencies) {
        Vault effectiveVault = vault.enabled() && !activeCurrencies.containsKey(vault.primaryCurrency())
                ? new Vault(false, vault.primaryCurrency(), vault.conflictPolicy())
                : vault;
        return new EconomySettings(networkKey, serverKey, databaseConnection, effectiveVault, messaging, cache,
                execution, activeCurrencies);
    }

    public record Vault(boolean enabled, String primaryCurrency, VaultConflictPolicy conflictPolicy) {
        public Vault {
            primaryCurrency = normalizeCurrencyId(primaryCurrency);
            Objects.requireNonNull(conflictPolicy, "conflictPolicy");
        }
    }

    public record Messaging(boolean enabled, String connection, String channel) {
        public Messaging {
            connection = EconomyConfigValues.requireText(connection, "messaging.connection");
            channel = EconomyConfigValues.requireText(channel, "messaging.channel");
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

    /** Dedicated Economy execution lane. It bounds database admission independently of Bukkit's async scheduler. */
    public record Execution(int workers, int queueCapacity, Duration synchronousTimeout, Duration shutdownDrain) {
        public Execution {
            if (workers < 1 || workers > 64) {
                throw new IllegalArgumentException("execution.workers must be between 1 and 64");
            }
            if (queueCapacity < 1 || queueCapacity > 100_000) {
                throw new IllegalArgumentException("execution.queue_capacity must be between 1 and 100000");
            }
            Objects.requireNonNull(synchronousTimeout, "synchronousTimeout");
            Objects.requireNonNull(shutdownDrain, "shutdownDrain");
            if (synchronousTimeout.isZero() || synchronousTimeout.isNegative()
                    || synchronousTimeout.compareTo(Duration.ofSeconds(30)) > 0) {
                throw new IllegalArgumentException("execution.synchronous_timeout must be between 1ms and 30s");
            }
            if (shutdownDrain.isNegative() || shutdownDrain.compareTo(Duration.ofSeconds(30)) > 0) {
                throw new IllegalArgumentException("execution.shutdown_drain must be between zero and 30s");
            }
        }

        public static Execution defaults() {
            return new Execution(4, 256, Duration.ofSeconds(2), Duration.ofSeconds(5));
        }
    }

    public record Currency(String id, EconomyScope scope, Display display, Balances balances,
                           Commands commands, Payments payments) {
        public Currency {
            id = normalizeCurrencyId(id);
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(display, "display");
            Objects.requireNonNull(balances, "balances");
            Objects.requireNonNull(commands, "commands");
            Objects.requireNonNull(payments, "payments");
        }
    }

    public record Display(String singular, String plural, String symbol, String format,
                          int fractionalDigits, boolean grouping) {
        public Display {
            singular = EconomyConfigValues.requireText(singular, "display.singular");
            plural = EconomyConfigValues.requireText(plural, "display.plural");
            symbol = symbol == null ? "" : symbol;
            format = EconomyConfigValues.requireText(format, "display.format");
        }
    }

    public record Balances(BigDecimal starting, BigDecimal minimum, BigDecimal maximum,
                           boolean allowNegative, RoundingMode rounding) {
        public Balances {
            Objects.requireNonNull(starting, "starting");
            Objects.requireNonNull(minimum, "minimum");
            Objects.requireNonNull(maximum, "maximum");
            Objects.requireNonNull(rounding, "rounding");
        }
    }

    public record Commands(String root, List<String> aliases, boolean balance, boolean balanceOthers,
                           boolean pay, boolean paytoggle, boolean history, boolean top) {
        public Commands {
            root = EconomyConfigValues.commandLabel(root);
            aliases = List.copyOf(aliases);
        }
    }

    public record Payments(boolean defaultEnabled, BigDecimal minimum, BigDecimal maximum,
                           BigDecimal confirmationThreshold, BigDecimal dailySendLimit,
                           BigDecimal dailyReceiveLimit, Duration cooldown) {
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
        /** Abort Economy startup when another Vault economy is already active. */
        FAIL,
        /** Keep the existing Vault economy and do not register this provider. */
        SKIP,
        /** Temporarily supersede the active provider without unregistering or owning it. */
        REPLACE
    }

    public static String normalizeCurrencyId(String value) {
        return EconomyConfigValues.currencyId(value);
    }
}
