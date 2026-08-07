package nl.hauntedmc.serverfeatures.api.economy;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/** Request for a one-account economy mutation. */
public record EconomyMutationRequest(
        String source,
        String idempotencyKey,
        EconomyAccountRef account,
        BigDecimal amount,
        Long actorPlayerId,
        String actorName,
        String reason,
        Map<String, String> metadata
) {
    public EconomyMutationRequest {
        source = EconomyRequestValidation.source(source);
        idempotencyKey = EconomyRequestValidation.text(idempotencyKey, "idempotencyKey", 160, true);
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(amount, "amount");
        if (actorPlayerId != null && actorPlayerId <= 0L) {
            throw new IllegalArgumentException("actorPlayerId must be positive when provided");
        }
        actorName = EconomyRequestValidation.text(actorName, "actorName", 64, false);
        reason = EconomyRequestValidation.text(reason, "reason", 255, false);
        metadata = EconomyRequestValidation.metadata(metadata);
    }

}
