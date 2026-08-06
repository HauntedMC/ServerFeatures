package nl.hauntedmc.serverfeatures.api.economy;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Result of a committed or rejected economy mutation. */
public record EconomyResult(
        EconomyResultStatus status,
        UUID operationId,
        BigDecimal balance,
        BigDecimal counterpartBalance,
        String message
) {
    public EconomyResult {
        Objects.requireNonNull(status, "status");
        message = message == null ? "" : message;
    }

    public boolean successful() {
        return status == EconomyResultStatus.SUCCESS || status == EconomyResultStatus.IDEMPOTENT_REPLAY;
    }

    public boolean replayed() {
        return status == EconomyResultStatus.IDEMPOTENT_REPLAY;
    }
}
