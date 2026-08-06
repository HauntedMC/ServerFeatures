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
        source = requireText(source, "source");
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(amount, "amount");
        actorName = actorName == null ? "" : actorName.trim();
        reason = reason == null ? "" : reason.trim();
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
