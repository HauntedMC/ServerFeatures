package nl.hauntedmc.serverfeatures.api.combat;

/**
 * Result of a combat-tag write operation.
 */
public enum CombatTagResult {
    TAGGED,
    RETAGGED,
    BYPASSED,
    WORLD_BLOCKED,
    INVALID
}
