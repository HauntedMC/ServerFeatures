package nl.hauntedmc.serverfeatures.api.economy;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/** Source-neutral wire request accepted by the authenticated Economy gateway. */
public record EconomyGatewayChargeRequest(
        String workflowId,
        EconomyAccountRef account,
        BigDecimal amount,
        Long actorPlayerId,
        String actorName,
        String reason,
        String eventType,
        Map<String, String> metadata
) {
    public EconomyGatewayChargeRequest {
        workflowId = EconomyRequestValidation.text(workflowId, "workflowId", 160, true);
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(amount, "amount");
        if (actorPlayerId != null && actorPlayerId <= 0L) {
            throw new IllegalArgumentException("actorPlayerId must be positive when provided");
        }
        actorName = EconomyRequestValidation.text(actorName, "actorName", 64, false);
        reason = EconomyRequestValidation.text(reason, "reason", 255, false);
        eventType = EconomyRequestValidation.eventType(eventType);
        metadata = EconomyRequestValidation.metadata(metadata);
    }
}
