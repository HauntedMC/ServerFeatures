package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import nl.hauntedmc.serverfeatures.features.invtools.model.InventorySnapshot;

import java.util.Objects;
import java.util.UUID;

public record OfflinePlayerData(
        UUID playerId,
        InventorySnapshot snapshot,
        PlayerDataRevision revision,
        int dataVersion,
        EquipmentStorageFormat equipmentStorageFormat,
        int runtimeDataVersion
) {
    public OfflinePlayerData {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(equipmentStorageFormat, "equipmentStorageFormat");
        if (runtimeDataVersion <= 0) {
            throw new IllegalArgumentException("runtimeDataVersion must be positive");
        }
    }

    public boolean supportsSafeEditing() {
        return dataVersion == runtimeDataVersion
                && equipmentStorageFormat == EquipmentStorageFormat.EQUIPMENT_COMPOUND;
    }
}
