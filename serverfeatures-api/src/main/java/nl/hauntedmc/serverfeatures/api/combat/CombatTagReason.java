package nl.hauntedmc.serverfeatures.api.combat;

/**
 * Describes the interaction source currently represented by a combat tag.
 *
 * <p>This is normally the latest qualifying interaction. When a dead displayed opponent is
 * replaced by a still-live retained incoming attacker, the reason follows that surviving
 * opponent instead.</p>
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
