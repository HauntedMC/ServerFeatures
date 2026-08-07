package nl.hauntedmc.serverfeatures.api.economy;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/** Request to charge an account and durably dispatch a domain fulfilment event after commit. */
public record EconomyWorkflowRequest(
        EconomyWorkflowRef workflow,
        EconomyAccountRef account,
        BigDecimal amount,
        Long actorPlayerId,
        String actorName,
        String reason,
        String eventType,
        Map<String, String> metadata
) {
    public EconomyWorkflowRequest {
        Objects.requireNonNull(workflow, "workflow");
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
