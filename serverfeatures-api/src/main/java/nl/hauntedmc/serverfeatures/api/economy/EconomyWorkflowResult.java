package nl.hauntedmc.serverfeatures.api.economy;

import java.util.Objects;
import java.util.UUID;

/** Authoritative charge result plus durable fulfilment state. */
public record EconomyWorkflowResult(
        EconomyResult transaction,
        EconomyWorkflowState state,
        UUID eventId,
        int attempts,
        String lastError
) {
    public EconomyWorkflowResult {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(state, "state");
        if (attempts < 0) {
            throw new IllegalArgumentException("attempts must not be negative");
        }
        lastError = lastError == null ? "" : lastError;
        if (transaction.successful() && eventId == null) {
            throw new IllegalArgumentException("a committed workflow requires an eventId");
        }
    }

    public boolean charged() {
        return transaction.successful();
    }
}
