package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import nl.hauntedmc.serverfeatures.features.invtools.model.InventoryKind;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventorySnapshot;

import java.io.IOException;
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

    void save(
            OfflinePlayerData original,
            InventoryKind kind,
            InventorySnapshot changedSnapshot
    ) throws IOException;
}
