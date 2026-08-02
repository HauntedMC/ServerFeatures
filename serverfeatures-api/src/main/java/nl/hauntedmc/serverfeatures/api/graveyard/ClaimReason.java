package nl.hauntedmc.serverfeatures.api.graveyard;

/**
 * Why a grave claim was initiated.
 */
public enum ClaimReason {
    PHYSICAL_INTERACTION,
    REMOTE_UNREACHABLE,
    STAFF_DELIVERY,
    PENDING_DELIVERY,
    ADMIN_RECOVERY
}
