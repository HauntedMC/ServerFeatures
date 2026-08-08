package nl.hauntedmc.serverfeatures.api.combat;

/**
 * Describes why a combat tag ended.
 */
public enum CombatUntagReason {
    EXPIRED,
    PLAYER_DEATH,
    OPPONENT_DEATH,
    WORLD_CHANGE,
    TELEPORT,
    LOGOUT,
    ADMINISTRATIVE,
    FEATURE_DISABLE,
    EXTERNAL
}
