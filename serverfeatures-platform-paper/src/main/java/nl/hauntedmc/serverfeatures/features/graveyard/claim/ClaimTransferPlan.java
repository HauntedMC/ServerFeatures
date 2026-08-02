package nl.hauntedmc.serverfeatures.features.graveyard.claim;

import nl.hauntedmc.serverfeatures.features.graveyard.capture.PlayerInventoryState;
import nl.hauntedmc.serverfeatures.features.graveyard.model.GravePayload;

public record ClaimTransferPlan(
        PlayerInventoryState resultingInventory,
        GravePayload remainingPayload,
        int transferredEntries,
        int transferredExperience,
        boolean changed
) {
}
