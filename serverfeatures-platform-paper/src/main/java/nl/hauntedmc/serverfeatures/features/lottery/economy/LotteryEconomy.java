package nl.hauntedmc.serverfeatures.features.lottery.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import nl.hauntedmc.serverfeatures.features.lottery.model.Money;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Legacy Vault Lottery backend. */
public final class LotteryEconomy implements LotteryEconomyGateway {
    private final Economy economy;

    private LotteryEconomy(Economy economy) {
        this.economy = Objects.requireNonNull(economy, "economy");
    }

    public static LotteryEconomy discover() {
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration == null || registration.getProvider() == null) {
            throw new IllegalStateException("Lottery VAULT backend requires Vault with an enabled economy provider");
        }
        return new LotteryEconomy(registration.getProvider());
    }

    @Override
    public Optional<Money> cachedBalance(OfflinePlayer player) {
        requireMainThread();
        return Optional.of(Money.fromVault(economy.getBalance(player)));
    }

    @Override
    public CompletionStage<EconomyResult> withdraw(
            OfflinePlayer player,
            Money amount,
            Operation operation,
            String idempotencyKey
    ) {
        requireMainThread();
        if (!amount.isPositive()) {
            return CompletableFuture.completedFuture(EconomyResult.failure("Amount must be positive"));
        }
        if (!economy.has(player, amount.toVault())) {
            return CompletableFuture.completedFuture(EconomyResult.failure("Insufficient funds"));
        }
        try {
            return CompletableFuture.completedFuture(result(economy.withdrawPlayer(player, amount.toVault())));
        } catch (RuntimeException | LinkageError exception) {
            return CompletableFuture.completedFuture(EconomyResult.uncertain(rootMessage(exception)));
        }
    }

    @Override
    public CompletionStage<EconomyResult> deposit(
            OfflinePlayer player,
            Money amount,
            Operation operation,
            String idempotencyKey
    ) {
        requireMainThread();
        if (!amount.isPositive()) {
            return CompletableFuture.completedFuture(EconomyResult.failure("Amount must be positive"));
        }
        try {
            return CompletableFuture.completedFuture(result(economy.depositPlayer(player, amount.toVault())));
        } catch (RuntimeException | LinkageError exception) {
            return CompletableFuture.completedFuture(EconomyResult.uncertain(rootMessage(exception)));
        }
    }

    @Override
    public String format(Money amount) {
        requireMainThread();
        try {
            return economy.format(amount.toVault());
        } catch (RuntimeException | LinkageError ignored) {
            return amount.plain();
        }
    }

    @Override
    public String backendName() {
        return "Vault:" + economy.getName();
    }

    private static EconomyResult result(EconomyResponse response) {
        if (response == null) {
            return EconomyResult.uncertain("Vault returned no EconomyResponse");
        }
        String message = response.errorMessage == null ? "" : response.errorMessage;
        if (response.transactionSuccess()) {
            return EconomyResult.success(message, "");
        }
        return EconomyResult.failure(message.isBlank() ? response.type.name() : message);
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Vault economy operations must run on the Paper main thread");
        }
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
