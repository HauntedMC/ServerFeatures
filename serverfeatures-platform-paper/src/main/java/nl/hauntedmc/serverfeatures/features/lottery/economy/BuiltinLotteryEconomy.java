package nl.hauntedmc.serverfeatures.features.lottery.economy;

import nl.hauntedmc.serverfeatures.api.economy.EconomyAccountRef;
import nl.hauntedmc.serverfeatures.api.economy.EconomyApi;
import nl.hauntedmc.serverfeatures.api.economy.EconomyMutationRequest;
import nl.hauntedmc.serverfeatures.api.economy.EconomyResultStatus;
import nl.hauntedmc.serverfeatures.features.lottery.model.Money;
import org.bukkit.OfflinePlayer;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/** Native ServerFeatures Economy backend for Lottery. */
public final class BuiltinLotteryEconomy implements LotteryEconomyGateway {
    private final EconomyApi economy;
    private final String currencyId;

    public BuiltinLotteryEconomy(EconomyApi economy, String currencyId) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.currencyId = Objects.requireNonNull(currencyId, "currencyId");
        var currency = economy.currency(currencyId).orElseThrow(() -> new IllegalStateException(
                "Lottery built-in currency is unavailable: " + currencyId
        ));
        if (currency.fractionalDigits() != Money.SCALE) {
            throw new IllegalStateException(
                    "Lottery requires a built-in currency with exactly " + Money.SCALE
                            + " fractional digits: " + currencyId
            );
        }
    }

    @Override
    public Optional<Money> cachedBalance(OfflinePlayer player) {
        return economy.cachedBalance(account(player)).map(balance -> Money.of(balance.balance()));
    }

    @Override
    public CompletionStage<EconomyResult> withdraw(OfflinePlayer player, Money amount, String idempotencyKey) {
        String type = idempotencyKey.startsWith("donation:") ? "LOTTERY_DONATION" : "LOTTERY_PURCHASE";
        EconomyMutationRequest request = request(player, amount, idempotencyKey, type);
        return executeIdempotently(() -> economy.withdraw(request), 1)
                .thenApply(BuiltinLotteryEconomy::result);
    }

    @Override
    public CompletionStage<EconomyResult> deposit(OfflinePlayer player, Money amount, String idempotencyKey) {
        String type = idempotencyKey.startsWith("payout:") ? "LOTTERY_PAYOUT" : "LOTTERY_REFUND";
        EconomyMutationRequest request = request(player, amount, idempotencyKey, type);
        return executeIdempotently(() -> economy.deposit(request), 1)
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

    private static CompletionStage<nl.hauntedmc.serverfeatures.api.economy.EconomyResult> executeIdempotently(
            Supplier<CompletionStage<nl.hauntedmc.serverfeatures.api.economy.EconomyResult>> operation,
            int retriesRemaining
    ) {
        CompletableFuture<nl.hauntedmc.serverfeatures.api.economy.EconomyResult> completion =
                new CompletableFuture<>();
        executeAttempt(operation, retriesRemaining, completion);
        return completion;
    }

    private static void executeAttempt(
            Supplier<CompletionStage<nl.hauntedmc.serverfeatures.api.economy.EconomyResult>> operation,
            int retriesRemaining,
            CompletableFuture<nl.hauntedmc.serverfeatures.api.economy.EconomyResult> completion
    ) {
        CompletionStage<nl.hauntedmc.serverfeatures.api.economy.EconomyResult> attempt;
        try {
            attempt = Objects.requireNonNull(operation.get(), "Economy API returned no completion stage");
        } catch (RuntimeException failure) {
            if (retriesRemaining > 0) {
                executeAttempt(operation, retriesRemaining - 1, completion);
            } else {
                completion.completeExceptionally(failure);
            }
            return;
        }
        attempt.whenComplete((result, failure) -> {
            boolean retryable = failure != null
                    || result != null && result.status() == EconomyResultStatus.TEMPORARY_FAILURE;
            if (retryable && retriesRemaining > 0) {
                executeAttempt(operation, retriesRemaining - 1, completion);
            } else if (failure != null) {
                completion.completeExceptionally(failure);
            } else if (result == null) {
                completion.completeExceptionally(new IllegalStateException("Economy API returned no result"));
            } else {
                completion.complete(result);
            }
        });
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
