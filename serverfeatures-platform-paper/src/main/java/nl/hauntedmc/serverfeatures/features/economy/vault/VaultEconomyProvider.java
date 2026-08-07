package nl.hauntedmc.serverfeatures.features.economy.vault;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import nl.hauntedmc.serverfeatures.features.economy.config.EconomySettings;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Account;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Identity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.MutationOutcome;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TransactionType;
import nl.hauntedmc.serverfeatures.features.economy.service.EconomyService;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Vault compatibility adapter for the configured primary currency.
 *
 * <p>The apparent size is largely imposed by Vault's legacy interface: every operation has name,
 * {@link OfflinePlayer}, and world overloads plus unsupported bank methods. World parameters are
 * intentionally ignored because scope is fixed by the configured currency, and authoritative
 * operations delegate to the synchronous service bridge. Vault has no asynchronous economy SPI,
 * so cache misses and mutations can block while canonical identity and database work complete.</p>
 */
@SuppressWarnings("deprecation")
public final class VaultEconomyProvider implements Economy {
    private final EconomyService service;
    private final String currencyId;
    private volatile boolean enabled = true;

    public VaultEconomyProvider(EconomyService service, String currencyId) {
        this.service = Objects.requireNonNull(service, "service");
        this.currencyId = Objects.requireNonNull(currencyId, "currencyId");
    }

    public void disable() {
        enabled = false;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getName() {
        return "ServerFeatures Economy";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return service.requireCurrency(currencyId).display().fractionalDigits();
    }

    @Override
    public String format(double amount) {
        return service.format(currencyId, decimal(amount));
    }

    @Override
    public String currencyNamePlural() {
        return service.requireCurrency(currencyId).display().plural();
    }

    @Override
    public String currencyNameSingular() {
        return service.requireCurrency(currencyId).display().singular();
    }

    @Override
    public boolean hasAccount(String playerName) {
        if (!enabled) {
            return false;
        }
        try {
            return service.resolveSync(playerName)
                    .map(identity -> service.hasAccountSync(identity, currencyId))
                    .orElse(false);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        if (!enabled) {
            return false;
        }
        try {
            return service.hasAccountSync(player, currencyId);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public double getBalance(String playerName) {
        if (!enabled) {
            return 0.0D;
        }
        try {
            return service.resolveSync(playerName)
                    .flatMap(identity -> service.balanceSync(identity, currencyId))
                    .map(Account::balance).map(BigDecimal::doubleValue).orElse(0.0D);
        } catch (RuntimeException exception) {
            return 0.0D;
        }
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        if (!enabled) {
            return 0.0D;
        }
        try {
            return service.balanceSync(player, currencyId)
                    .map(Account::balance).map(BigDecimal::doubleValue).orElse(0.0D);
        } catch (RuntimeException exception) {
            return 0.0D;
        }
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(String playerName, double amount) {
        return hasResolved(() -> service.resolveSync(playerName), amount);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return hasResolved(() -> service.resolveSync(player), amount);
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return mutateResolved(() -> service.resolveSync(playerName), amount, TransactionType.WITHDRAW);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        return mutateResolved(() -> service.resolveSync(player), amount, TransactionType.WITHDRAW);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return mutateResolved(() -> service.resolveSync(playerName), amount, TransactionType.DEPOSIT);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        return mutateResolved(() -> service.resolveSync(player), amount, TransactionType.DEPOSIT);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return notImplemented();
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return notImplemented();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return notImplemented();
    }

    @Override
    public List<String> getBanks() {
        return List.of();
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        if (!enabled) {
            return false;
        }
        try {
            return service.resolveSync(playerName)
                    .flatMap(identity -> service.balanceSync(identity, currencyId))
                    .isPresent();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        if (!enabled) {
            return false;
        }
        try {
            return service.balanceSync(player, currencyId).isPresent();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    private EconomyResponse mutate(Identity identity, BigDecimal amount, TransactionType type) {
        if (amount.signum() == 0) {
            return new EconomyResponse(
                    0.0D,
                    safeCurrent(identity),
                    EconomyResponse.ResponseType.SUCCESS,
                    ""
            );
        }
        MutationOutcome outcome;
        try {
            outcome = service.mutateSync(
                    identity,
                    currencyId,
                    amount,
                    type,
                    "vault",
                    UUID.randomUUID().toString()
            );
        } catch (RuntimeException exception) {
            return failure(safeCurrent(identity), rootMessage(exception));
        }
        if (outcome == null) {
            return failure(safeCurrent(identity), "Economy mutation returned no result");
        }
        if (!outcome.successful()) {
            String message = outcome.message();
            return failure(outcome.balance() == null ? safeCurrent(identity) : outcome.balance().doubleValue(),
                    message == null || message.isBlank() ? outcome.status().name() : message);
        }
        if (outcome.balance() == null) {
            return failure(safeCurrent(identity), "Economy mutation returned no resulting balance");
        }
        return new EconomyResponse(
                amount.doubleValue(),
                outcome.balance().doubleValue(),
                EconomyResponse.ResponseType.SUCCESS,
                ""
        );
    }

    private EconomyResponse mutateResolved(
            java.util.function.Supplier<Optional<Identity>> resolver,
            double rawAmount,
            TransactionType type
    ) {
        if (!enabled) {
            return failure(0.0D, "Economy provider is disabled");
        }
        if (!valid(rawAmount) || rawAmount < 0.0D) {
            return failure(0.0D, "Amount must be finite and non-negative");
        }
        try {
            BigDecimal amount = normalizedAmount(rawAmount);
            if (rawAmount > 0.0D && amount.signum() == 0) {
                return failure(0.0D, "Amount is smaller than the currency precision");
            }
            return resolver.get()
                    .map(identity -> mutate(identity, amount, type))
                    .orElseGet(() -> failure(0.0D, "Unknown player"));
        } catch (RuntimeException exception) {
            return failure(0.0D, rootMessage(exception));
        }
    }

    private boolean hasResolved(java.util.function.Supplier<Optional<Identity>> resolver, double rawAmount) {
        if (!enabled || !valid(rawAmount) || rawAmount < 0.0D) {
            return false;
        }
        try {
            BigDecimal amount = normalizedAmount(rawAmount);
            if (rawAmount > 0.0D && amount.signum() == 0) {
                return false;
            }
            return resolver.get()
                    .flatMap(identity -> service.balanceSync(identity, currencyId))
                    .map(Account::balance)
                    .map(balance -> balance.compareTo(amount) >= 0)
                    .orElse(false);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private double safeCurrent(Identity identity) {
        try {
            return service.balanceSync(identity, currencyId)
                    .map(Account::balance)
                    .map(BigDecimal::doubleValue)
                    .orElse(0.0D);
        } catch (RuntimeException exception) {
            return 0.0D;
        }
    }

    private static EconomyResponse failure(double balance, String message) {
        return new EconomyResponse(0.0D, balance, EconomyResponse.ResponseType.FAILURE, message);
    }

    private static EconomyResponse notImplemented() {
        return new EconomyResponse(0.0D, 0.0D, EconomyResponse.ResponseType.NOT_IMPLEMENTED,
                "Bank accounts are not supported");
    }

    private static BigDecimal decimal(double amount) {
        if (!valid(amount)) {
            throw new IllegalArgumentException("Amount must be finite");
        }
        return BigDecimal.valueOf(amount);
    }

    private BigDecimal normalizedAmount(double amount) {
        EconomySettings.Currency currency = service.requireCurrency(currencyId);
        return decimal(amount).setScale(
                currency.display().fractionalDigits(),
                currency.balances().rounding()
        );
    }

    private static boolean valid(double amount) {
        return Double.isFinite(amount);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
