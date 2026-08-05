package nl.hauntedmc.serverfeatures.features.lottery.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import nl.hauntedmc.serverfeatures.features.lottery.model.Money;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Objects;

/** Main-thread-only Vault economy boundary. */
public final class LotteryEconomy {

    private final Economy economy;

    private LotteryEconomy(Economy economy) {
        this.economy = Objects.requireNonNull(economy, "economy");
    }

    public static LotteryEconomy discover() {
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration == null || registration.getProvider() == null) {
            throw new IllegalStateException("Lottery requires Vault with an enabled economy provider");
        }
        return new LotteryEconomy(registration.getProvider());
    }

    public Money balance(OfflinePlayer player) {
        requireMainThread();
        return Money.fromVault(economy.getBalance(player));
    }

    public EconomyResult withdraw(OfflinePlayer player, Money amount) {
        requireMainThread();
        if (!amount.isPositive()) {
            return EconomyResult.failure("Amount must be positive");
        }
        if (!economy.has(player, amount.toVault())) {
            return EconomyResult.failure("Insufficient funds");
        }
        try {
            return result(economy.withdrawPlayer(player, amount.toVault()));
        } catch (RuntimeException | LinkageError exception) {
            return EconomyResult.uncertain(rootMessage(exception));
        }
    }

    public EconomyResult deposit(OfflinePlayer player, Money amount) {
        requireMainThread();
        if (!amount.isPositive()) {
            return EconomyResult.failure("Amount must be positive");
        }
        try {
            return result(economy.depositPlayer(player, amount.toVault()));
        } catch (RuntimeException | LinkageError exception) {
            return EconomyResult.uncertain(rootMessage(exception));
        }
    }

    public String format(Money amount) {
        requireMainThread();
        try {
            return economy.format(amount.toVault());
        } catch (RuntimeException | LinkageError ignored) {
            return amount.plain();
        }
    }

    private static EconomyResult result(EconomyResponse response) {
        if (response == null) {
            return EconomyResult.uncertain("Vault returned no EconomyResponse");
        }
        String message = response.errorMessage == null ? "" : response.errorMessage;
        if (response.transactionSuccess()) {
            return EconomyResult.success(message);
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

    public record EconomyResult(boolean successful, boolean uncertain, String message) {
        public EconomyResult {
            message = message == null ? "" : message;
        }

        public static EconomyResult success(String message) {
            return new EconomyResult(true, false, message);
        }

        public static EconomyResult failure(String message) {
            return new EconomyResult(false, false, message);
        }

        public static EconomyResult uncertain(String message) {
            return new EconomyResult(false, true, message);
        }
    }
}
