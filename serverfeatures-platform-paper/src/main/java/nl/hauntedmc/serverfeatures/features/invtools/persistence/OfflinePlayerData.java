package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import nl.hauntedmc.serverfeatures.features.invtools.model.InventorySnapshot;

import java.util.Objects;
import java.util.UUID;

public record OfflinePlayerData(
        UUID playerId,
        InventorySnapshot snapshot,
        PlayerDataRevision revision,
        int dataVersion,
        EquipmentStorageFormat equipmentStorageFormat
) {
    public OfflinePlayerData {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(equipmentStorageFormat, "equipmentStorageFormat");
    }

    public boolean supportsSafeEditing() {
        return dataVersion >= NbtOfflinePlayerDataStore.EQUIPMENT_COMPOUND_DATA_VERSION
                && dataVersion <= NbtOfflinePlayerDataStore.CURRENT_SUPPORTED_DATA_VERSION;
    }
}
