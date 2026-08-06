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
        source = requireText(source, "source");
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(account, "account");
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
