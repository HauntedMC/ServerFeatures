package nl.hauntedmc.serverfeatures.features.sanctions.entity;

/**
 * Database values used by the shared {@code player_sanctions.type} column.
 *
 * <p>The values intentionally mirror the ProxyFeatures sanctions schema without depending on
 * ProxyFeatures' Velocity-runtime persistence classes.</p>
 */
public enum SanctionType {
    BAN,
    BAN_IP,
    MUTE,
    WARN,
    KICK
}
