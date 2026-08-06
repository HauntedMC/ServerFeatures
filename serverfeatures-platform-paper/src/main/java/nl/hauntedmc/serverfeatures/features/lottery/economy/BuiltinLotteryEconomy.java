package nl.hauntedmc.serverfeatures.features.lottery.economy;

import nl.hauntedmc.serverfeatures.api.economy.EconomyAccountRef;
import nl.hauntedmc.serverfeatures.api.economy.EconomyApi;
import nl.hauntedmc.serverfeatures.api.economy.EconomyMutationRequest;
import nl.hauntedmc.serverfeatures.api.economy.EconomyResultStatus;
import nl.hauntedmc.serverfeatures.features.lottery.model.Money;
import org.bukkit.OfflinePlayer;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Native ServerFeatures Economy backend for Lottery. */
public final class BuiltinLotteryEconomy implements LotteryEconomyGateway {
    private final EconomyApi economy;
    private final String currencyId;

    public BuiltinLotteryEconomy(EconomyApi economy, String currencyId) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.currencyId = Objects.requireNonNull(currencyId, "currencyId");
        economy.currency(currencyId).orElseThrow(() -> new IllegalStateException(
                "Lottery built-in currency is unavailable: " + currencyId
        ));
    }

    @Override
    public Money cachedBalance(OfflinePlayer player) {
        return economy.cachedBalance(account(player)).map(balance -> Money.of(balance.balance())).orElse(Money.ZERO);
    }

    @Override
    public CompletionStage<EconomyResult> withdraw(OfflinePlayer player, Money amount, String idempotencyKey) {
        String type = idempotencyKey.startsWith("donation:") ? "LOTTERY_DONATION" : "LOTTERY_PURCHASE";
        return economy.withdraw(request(player, amount, idempotencyKey, type))
                .thenApply(BuiltinLotteryEconomy::result);
    }

    @Override
    public CompletionStage<EconomyResult> deposit(OfflinePlayer player, Money amount, String idempotencyKey) {
        String type = idempotencyKey.startsWith("payout:") ? "LOTTERY_PAYOUT" : "LOTTERY_REFUND";
        return economy.deposit(request(player, amount, idempotencyKey, type))
                .thenApply(BuiltinLotteryEconomy::result);
    }

    @Override
    public String format(Money amount) {
        return economy.format(currencyId, amount.amount());
    }

    @Override
    public String backendName() {
        return "Builtin:" + currencyId;
    }

    private EconomyMutationRequest request(
            OfflinePlayer player,
            Money amount,
            String idempotencyKey,
            String transactionType
    ) {
        return new EconomyMutationRequest(
                "lottery",
                idempotencyKey,
                account(player),
                amount.amount(),
                null,
                "Lottery",
                transactionType,
                Map.of("transaction_type", transactionType)
        );
    }

    private EconomyAccountRef account(OfflinePlayer player) {
        var currency = economy.currency(currencyId).orElseThrow();
        return new EconomyAccountRef(
                null,
                player.getUniqueId(),
                player.getName(),
                currency.id(),
                currency.scope().key()
        );
    }

    private static EconomyResult result(nl.hauntedmc.serverfeatures.api.economy.EconomyResult result) {
        if (result.successful()) {
            return EconomyResult.success(
                    result.message(),
                    result.operationId() == null ? "" : result.operationId().toString()
            );
        }
        if (result.status() == EconomyResultStatus.TEMPORARY_FAILURE) {
            return EconomyResult.uncertain(result.message());
        }
        return EconomyResult.failure(result.message().isBlank() ? result.status().name() : result.message());
    }
}
