package nl.hauntedmc.serverfeatures.api.economy;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/** Request for an atomic transfer between two accounts in one currency scope. */
public record EconomyTransferRequest(
        String source,
        String idempotencyKey,
        EconomyAccountRef sender,
        EconomyAccountRef recipient,
        BigDecimal amount,
        Long actorPlayerId,
        String actorName,
        String reason,
        Map<String, String> metadata,
        boolean bypassPaymentsToggle
) {
    public EconomyTransferRequest {
        source = EconomyRequestValidation.source(source);
        idempotencyKey = EconomyRequestValidation.text(idempotencyKey, "idempotencyKey", 160, true);
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(amount, "amount");
        if (actorPlayerId != null && actorPlayerId <= 0L) {
            throw new IllegalArgumentException("actorPlayerId must be positive when provided");
        }
        actorName = EconomyRequestValidation.text(actorName, "actorName", 64, false);
        reason = EconomyRequestValidation.text(reason, "reason", 255, false);
        metadata = EconomyRequestValidation.metadata(metadata);
    }

}
