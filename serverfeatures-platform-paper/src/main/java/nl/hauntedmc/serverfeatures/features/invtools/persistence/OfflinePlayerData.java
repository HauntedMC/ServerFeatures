package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import nl.hauntedmc.serverfeatures.features.invtools.model.InventorySnapshot;

import java.util.Objects;
import java.util.UUID;

public record OfflinePlayerData(
        UUID playerId,
        InventorySnapshot snapshot,
        PlayerDataRevision revision
) {
    public OfflinePlayerData {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(revision, "revision");
    }
}
