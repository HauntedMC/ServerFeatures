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
     * Loads data when it is present for the player's current identity. Implementations may handle
     * the current server's documented playerdata migration rules before returning the durable
     * source file; callers must persist changes through the returned data's player ID.
     */
    default Optional<OfflinePlayerData> loadIfPresent(
            UUID playerId,
            String playerName,
            boolean onlineMode
    ) throws IOException {
        if (!hasPlayerData(playerId)) {
            return Optional.empty();
        }
        return Optional.of(load(playerId));
    }

    void save(
            OfflinePlayerData original,
            InventoryKind kind,
            InventorySnapshot changedSnapshot
    ) throws IOException;
}
