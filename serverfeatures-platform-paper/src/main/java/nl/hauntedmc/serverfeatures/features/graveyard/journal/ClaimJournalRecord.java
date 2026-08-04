package nl.hauntedmc.serverfeatures.features.graveyard.journal;

import nl.hauntedmc.serverfeatures.features.graveyard.persistence.EncodedGravePayload;

import java.util.Objects;
import java.util.UUID;

public record ClaimJournalRecord(
        UUID operationToken,
        ClaimJournalState state,
        UUID graveId,
        UUID ownerUuid,
        UUID actorUuid,
        long previousRevision,
        int transferredEntries,
        int transferredExperience,
        EncodedGravePayload remainingPayload
) {
    public ClaimJournalRecord {
        Objects.requireNonNull(operationToken, "operationToken");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(graveId, "graveId");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(actorUuid, "actorUuid");
        Objects.requireNonNull(remainingPayload, "remainingPayload");
    }

    public ClaimJournalRecord withState(ClaimJournalState next) {
        return new ClaimJournalRecord(
                operationToken,
                next,
                graveId,
                ownerUuid,
                actorUuid,
                previousRevision,
                transferredEntries,
                transferredExperience,
                remainingPayload
        );
    }
}
