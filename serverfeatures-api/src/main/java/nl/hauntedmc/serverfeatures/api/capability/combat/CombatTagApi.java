package nl.hauntedmc.serverfeatures.api.capability.combat;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only public capability for the currently active CombatTag feature.
 *
 * <p>The contract deliberately uses only stable JDK/domain types. Paper entity adaptation and
 * combat mutation remain owned by the Paper runtime so external consumers cannot bypass combat
 * policy, world restrictions, attribution rules, or lifecycle ownership.</p>
 */
public interface CombatTagApi {

    boolean isTagged(UUID playerId);

    Optional<CombatTagSnapshot> getTag(UUID playerId);
}
