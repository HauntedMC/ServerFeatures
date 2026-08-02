package nl.hauntedmc.serverfeatures.api.graveyard;

import java.util.UUID;

/**
 * Immutable result of one grave claim attempt.
 */
public record GraveClaimResult(
        UUID graveId,
        GraveClaimOutcome outcome,
        int transferredEntries,
        int remainingEntries,
        int transferredExperience,
        String message
) {
}
