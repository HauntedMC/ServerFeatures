package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the authoritative Minecraft identity for an InvTools command argument.
 *
 * <p>The lookup may consult persistence and therefore must only be invoked away from Paper's main
 * thread. Implementations must not call Bukkit APIs while resolving an identity.</p>
 */
@FunctionalInterface
interface CanonicalPlayerIdentityLookup {

    Optional<Identity> find(String identifier) throws IOException;

    static CanonicalPlayerIdentityLookup none() {
        return ignored -> Optional.empty();
    }

    record Identity(UUID playerId, String playerName) {

        Identity {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(playerName, "playerName");
            if (playerName.isBlank()) {
                throw new IllegalArgumentException("playerName must not be blank");
            }
        }
    }
}
