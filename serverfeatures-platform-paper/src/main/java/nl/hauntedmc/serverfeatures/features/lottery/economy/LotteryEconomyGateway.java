package nl.hauntedmc.serverfeatures.features.lottery.economy;

import nl.hauntedmc.serverfeatures.features.lottery.model.Money;
import org.bukkit.OfflinePlayer;

import java.util.concurrent.CompletionStage;

/** Selected monetary backend for one Lottery instance. */
public interface LotteryEconomyGateway {
    Money cachedBalance(OfflinePlayer player);

    CompletionStage<EconomyResult> withdraw(OfflinePlayer player, Money amount, String idempotencyKey);

    CompletionStage<EconomyResult> deposit(OfflinePlayer player, Money amount, String idempotencyKey);

    String format(Money amount);

    String backendName();

    record EconomyResult(boolean successful, boolean uncertain, String message, String operationId) {
        public EconomyResult {
            message = message == null ? "" : message;
            operationId = operationId == null ? "" : operationId;
        }

        public static EconomyResult success(String message, String operationId) {
            return new EconomyResult(true, false, message, operationId);
        }

        public static EconomyResult failure(String message) {
            return new EconomyResult(false, false, message, "");
        }

        public static EconomyResult uncertain(String message) {
            return new EconomyResult(false, true, message, "");
        }
    }
}
