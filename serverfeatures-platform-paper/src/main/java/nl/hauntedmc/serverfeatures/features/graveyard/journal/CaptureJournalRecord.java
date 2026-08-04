package nl.hauntedmc.serverfeatures.features.graveyard.journal;

import nl.hauntedmc.serverfeatures.features.graveyard.model.Grave;
import nl.hauntedmc.serverfeatures.features.graveyard.persistence.EncodedGravePayload;

import java.util.Objects;
import java.util.UUID;

public record CaptureJournalRecord(
        UUID operationToken,
        CaptureJournalState state,
        Grave grave,
        EncodedGravePayload payload
) {
    public CaptureJournalRecord {
        Objects.requireNonNull(operationToken, "operationToken");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(grave, "grave");
        Objects.requireNonNull(payload, "payload");
    }

    public CaptureJournalRecord withState(CaptureJournalState next) {
        return new CaptureJournalRecord(operationToken, next, grave, payload);
    }
}
