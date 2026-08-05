package nl.hauntedmc.serverfeatures.api.combat;

/**
 * Describes the source that most recently refreshed a combat tag.
 */
public enum CombatTagReason {
    MELEE,
    PROJECTILE,
    PET,
    FISHING_HOOK,
    TNT,
    AREA_EFFECT,
    FIREWORK,
    EVOKER_FANGS,
    EXPLOSION,
    INDIRECT,
    EXTERNAL
}
