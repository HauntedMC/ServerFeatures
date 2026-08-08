package nl.hauntedmc.serverfeatures.api.combat;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Public runtime API for the native ServerFeatures combat-tag implementation.
 *
 * <p>Reads are safe from any thread. Tag and untag writes must be called from the server thread.</p>
 */
public interface CombatTagApi {

    boolean isTagged(UUID playerId);

    default boolean isTagged(Player player) {
        return player != null && isTagged(player.getUniqueId());
    }

    Optional<CombatTagSnapshot> getTag(UUID playerId);

    default Optional<CombatTagSnapshot> getTag(Player player) {
        return player == null ? Optional.empty() : getTag(player.getUniqueId());
    }

    CombatTagResult tag(Player player, Entity opponent, CombatTagReason reason);

    boolean untag(Player player, CombatUntagReason reason);
}
