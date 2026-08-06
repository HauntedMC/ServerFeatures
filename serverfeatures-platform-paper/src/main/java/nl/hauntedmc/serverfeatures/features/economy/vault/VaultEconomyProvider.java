package nl.hauntedmc.serverfeatures.features.economy.vault;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Account;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.Identity;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.MutationOutcome;
import nl.hauntedmc.serverfeatures.features.economy.model.EconomyModels.TransactionType;
import nl.hauntedmc.serverfeatures.features.economy.service.EconomyService;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Complete Vault compatibility adapter for the configured primary currency. */
@SuppressWarnings("deprecation")
public final class VaultEconomyProvider implements Economy {
    private final EconomyService service;
    private final String currencyId;
    private volatile boolean enabled = true;

    public VaultEconomyProvider(EconomyService service, String currencyId) {
        this.service = service;
        this.currencyId = currencyId;
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
        if (!valid(amount) || amount < 0.0D) {
            return false;
        }
        try {
            return service.resolveSync(playerName)
                    .map(identity -> service.hasBalanceSync(identity, currencyId, decimal(amount)))
                    .orElse(false);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        if (!valid(amount) || amount < 0.0D) {
            return false;
        }
        try {
            return service.resolveSync(player)
                    .map(identity -> service.hasBalanceSync(identity, currencyId, decimal(amount)))
                    .orElse(false);
        } catch (RuntimeException exception) {
            return false;
        }
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
        return mutateResolved(() -> service.resolveSync(playerName), amount, TransactionType.VAULT_WITHDRAW);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        return mutateResolved(() -> service.resolveSync(player), amount, TransactionType.VAULT_WITHDRAW);
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
        return mutateResolved(() -> service.resolveSync(playerName), amount, TransactionType.VAULT_DEPOSIT);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        return mutateResolved(() -> service.resolveSync(player), amount, TransactionType.VAULT_DEPOSIT);
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

    private EconomyResponse mutate(Identity identity, double rawAmount, TransactionType type) {
        if (!enabled) {
            return failure(rawAmount, 0.0D, "Economy provider is disabled");
        }
        if (!valid(rawAmount) || rawAmount < 0.0D) {
            return failure(rawAmount, safeCurrent(identity), "Amount must be finite and non-negative");
        }
        if (rawAmount == 0.0D) {
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
                    decimal(rawAmount),
                    type,
                    "vault",
                    UUID.randomUUID().toString()
            );
        } catch (RuntimeException exception) {
            return failure(rawAmount, safeCurrent(identity), rootMessage(exception));
        }
        if (!outcome.successful()) {
            return failure(rawAmount, outcome.balance() == null ? safeCurrent(identity) : outcome.balance().doubleValue(),
                    outcome.message().isBlank() ? outcome.status().name() : outcome.message());
        }
        return new EconomyResponse(
                rawAmount,
                outcome.balance().doubleValue(),
                EconomyResponse.ResponseType.SUCCESS,
                ""
        );
    }

    private EconomyResponse mutateResolved(
            java.util.function.Supplier<Optional<Identity>> resolver,
            double amount,
            TransactionType type
    ) {
        try {
            return resolver.get()
                    .map(identity -> mutate(identity, amount, type))
                    .orElseGet(() -> failure(amount, 0.0D, "Unknown player"));
        } catch (RuntimeException exception) {
            return failure(amount, 0.0D, rootMessage(exception));
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

    private static EconomyResponse failure(double amount, double balance, String message) {
        return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.FAILURE, message);
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
