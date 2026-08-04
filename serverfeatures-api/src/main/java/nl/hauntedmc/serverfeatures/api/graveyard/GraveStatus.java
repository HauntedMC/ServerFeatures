package nl.hauntedmc.serverfeatures.api.graveyard;

/**
 * Durable lifecycle state of a Graveyard grave.
 */
public enum GraveStatus {
    ACTIVE,
    PARTIAL,
    ORPHANED_WORLD,
    DELIVERY_PENDING,
    CLAIMED,
    EXPIRED,
    CORRUPT,
    ADMIN_RECOVERED,
    PURGED;

    public boolean isVisible() {
        return this == ACTIVE || this == PARTIAL;
    }

    public boolean isPlayerClaimable() {
        return this == ACTIVE || this == PARTIAL || this == ORPHANED_WORLD;
    }

    public boolean hasRecoverablePayload() {
        return this != CLAIMED && this != ADMIN_RECOVERED && this != PURGED;
    }
}
