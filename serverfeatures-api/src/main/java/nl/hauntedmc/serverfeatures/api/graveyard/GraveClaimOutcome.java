package nl.hauntedmc.serverfeatures.api.graveyard;

/**
 * Terminal result reported by a Graveyard claim request.
 */
public enum GraveClaimOutcome {
    CLAIMED,
    PARTIAL,
    DELIVERY_QUEUED,
    RECOVERY_PENDING,
    NOTHING_FIT,
    NOT_FOUND,
    NOT_OWNER,
    NOT_CLAIMABLE,
    WRONG_INVENTORY_SCOPE,
    BUSY,
    FAILED
}
