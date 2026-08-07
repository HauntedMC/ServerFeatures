package nl.hauntedmc.serverfeatures.features.lottery.economy;

import nl.hauntedmc.serverfeatures.api.economy.EconomyAccountRef;
import nl.hauntedmc.serverfeatures.api.economy.EconomyApi;
import nl.hauntedmc.serverfeatures.api.economy.EconomyMutationRequest;
import nl.hauntedmc.serverfeatures.api.economy.EconomyResultStatus;
import nl.hauntedmc.serverfeatures.api.economy.EconomyWorkflowEvent;
import nl.hauntedmc.serverfeatures.api.economy.EconomyWorkflowHandler;
import nl.hauntedmc.serverfeatures.api.economy.EconomyWorkflowRef;
import nl.hauntedmc.serverfeatures.api.economy.EconomyWorkflowRegistration;
import nl.hauntedmc.serverfeatures.api.economy.EconomyWorkflowRequest;
import nl.hauntedmc.serverfeatures.api.economy.EconomyWorkflowResult;
import nl.hauntedmc.serverfeatures.features.lottery.model.Money;
import org.bukkit.OfflinePlayer;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
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
    public CompletionStage<EconomyResult> withdraw(
            OfflinePlayer player,
            Money amount,
            Operation operation,
            String idempotencyKey
    ) {
        requireWithdrawal(operation);
        EconomyMutationRequest request = request(player, amount, operation, idempotencyKey);
        return executeIdempotently(() -> economy.withdraw(request), 1)
                .thenApply(BuiltinLotteryEconomy::result);
    }

    @Override
    public CompletionStage<EconomyResult> deposit(
            OfflinePlayer player,
            Money amount,
            Operation operation,
            String idempotencyKey
    ) {
        requireDeposit(operation);
        EconomyMutationRequest request = request(player, amount, operation, idempotencyKey);
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

    /** Atomically commits a Lottery debit and its subsequent fulfilment event. */
    public CompletionStage<EconomyWorkflowResult> chargePurchase(
            UUID playerUuid,
            String playerName,
            long playerId,
            Money amount,
            String purchaseIntentId
    ) {
        EconomyWorkflowRequest request = new EconomyWorkflowRequest(
                new EconomyWorkflowRef("lottery", purchaseIntentId),
                account(playerUuid, playerName, playerId),
                amount.amount(),
                playerId,
                playerName,
                "Lottery ticket purchase",
                "lottery.purchase.v1",
                Map.of("purchase_intent_id", purchaseIntentId)
        );
        return economy.chargeAndDispatch(request);
    }

    /** Registers Lottery's idempotent durable-purchase fulfiller with the native Economy. */
    public EconomyWorkflowRegistration registerPurchaseHandler(EconomyWorkflowHandler handler) {
        return economy.registerWorkflowHandler("lottery.purchase.v1", handler);
    }

    /** Reads the durable state before safely retrying an uncertain charge. */
    public CompletionStage<Optional<EconomyWorkflowResult>> purchaseWorkflow(String purchaseIntentId) {
        return economy.workflow(new EconomyWorkflowRef("lottery", purchaseIntentId));
    }

    /** Compensates an unfulfillable charged purchase with a retry-safe native refund. */
    public CompletionStage<nl.hauntedmc.serverfeatures.api.economy.EconomyResult> refundPurchase(
            EconomyWorkflowEvent event
    ) {
        if (!currencyId.equals(event.account().currencyId())) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Lottery workflow uses another currency"));
        }
        EconomyMutationRequest request = new EconomyMutationRequest(
                "lottery",
                "refund:workflow:" + event.eventId(),
                event.account(),
                event.amount(),
                event.account().playerId(),
                event.account().playerName(),
                "Lottery ticket purchase refund",
                Map.of("workflow_event_id", event.eventId().toString())
        );
        return executeIdempotently(() -> economy.deposit(request), 1);
    }

    private EconomyMutationRequest request(
            OfflinePlayer player,
            Money amount,
            Operation operation,
            String idempotencyKey
    ) {
        return new EconomyMutationRequest(
                "lottery",
                idempotencyKey,
                account(player),
                amount.amount(),
                null,
                "Lottery",
                reason(operation),
                Map.of("lottery_operation", operation.name().toLowerCase(java.util.Locale.ROOT))
        );
    }

    private static void requireWithdrawal(Operation operation) {
        if (operation != Operation.PURCHASE && operation != Operation.DONATION) {
            throw new IllegalArgumentException("Lottery " + operation + " must use a deposit");
        }
    }

    private static void requireDeposit(Operation operation) {
        if (operation != Operation.PAYOUT && operation != Operation.REFUND) {
            throw new IllegalArgumentException("Lottery " + operation + " must use a withdrawal");
        }
    }

    private static String reason(Operation operation) {
        return switch (operation) {
            case PURCHASE -> "Lottery ticket purchase";
            case DONATION -> "Lottery pot donation";
            case PAYOUT -> "Lottery payout";
            case REFUND -> "Lottery refund";
        };
    }

    private EconomyAccountRef account(OfflinePlayer player) {
        return account(player.getUniqueId(), player.getName(), null);
    }

    private EconomyAccountRef account(UUID playerUuid, String playerName, Long playerId) {
        var currency = economy.currency(currencyId).orElseThrow();
        return new EconomyAccountRef(
                playerId,
                playerUuid,
                playerName,
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
