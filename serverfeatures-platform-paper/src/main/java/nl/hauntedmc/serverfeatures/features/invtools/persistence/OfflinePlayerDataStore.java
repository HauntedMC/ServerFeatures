package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import nl.hauntedmc.serverfeatures.features.invtools.model.InventoryKind;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventorySnapshot;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Blocking storage boundary. Callers must execute this interface away from the server thread.
 */
public interface OfflinePlayerDataStore {

    /**
     * Returns whether this server's primary world has a regular playerdata file for the UUID.
     */
    boolean hasPlayerData(UUID playerId) throws IOException;

    OfflinePlayerData load(UUID playerId) throws IOException;

    /**
     * Resolves a name to the strongest available UUID candidate for this server.
     *
     * <p>An authoritative identity may be returned before its playerdata file is visible. This is
     * required during logout because Paper fires the quit event before the final player save has
     * necessarily reached disk. The caller is responsible for waiting for the file to appear.</p>
     */
    default Optional<UUID> resolvePlayerId(
            Optional<UUID> preferredPlayerId,
            String playerName
    ) throws IOException {
        if (preferredPlayerId.isEmpty() || !hasPlayerData(preferredPlayerId.get())) {
            return Optional.empty();
        }
        return preferredPlayerId;
    }

    /**
     * Records an identity observed directly from a successful player connection.
     */
    default void rememberPlayerIdentity(UUID playerId, String playerName) {
    }

    void save(
            OfflinePlayerData original,
            InventoryKind kind,
            InventorySnapshot changedSnapshot
    ) throws IOException;
}
