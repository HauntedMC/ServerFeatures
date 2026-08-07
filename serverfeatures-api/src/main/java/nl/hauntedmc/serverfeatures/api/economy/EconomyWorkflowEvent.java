package nl.hauntedmc.serverfeatures.api.economy;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** A committed outbox event delivered at least once to an idempotent fulfilment handler. */
public record EconomyWorkflowEvent(
        UUID eventId,
        EconomyWorkflowRef workflow,
        UUID operationId,
        EconomyAccountRef account,
        BigDecimal amount,
        String eventType,
        Map<String, String> metadata,
        long createdAt
) {
    public EconomyWorkflowEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(workflow, "workflow");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(amount, "amount");
        eventType = EconomyRequestValidation.eventType(eventType);
        metadata = EconomyRequestValidation.metadata(metadata);
        if (createdAt < 0L) {
            throw new IllegalArgumentException("createdAt must not be negative");
        }
    }
}
