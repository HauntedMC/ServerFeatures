package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import nl.hauntedmc.serverfeatures.features.invtools.model.InventorySnapshot;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflinePlayerDataTest {

    @Test
    void onlySupportedModernPlayerdataIsEligibleForOfflineEditing() {
        OfflinePlayerData legacy = data(
                NbtOfflinePlayerDataStore.EQUIPMENT_COMPOUND_DATA_VERSION - 1,
                EquipmentStorageFormat.LEGACY_INVENTORY_SLOTS
        );
        OfflinePlayerData modern = data(
                NbtOfflinePlayerDataStore.EQUIPMENT_COMPOUND_DATA_VERSION,
                EquipmentStorageFormat.EQUIPMENT_COMPOUND
        );
        OfflinePlayerData future = data(
                NbtOfflinePlayerDataStore.CURRENT_SUPPORTED_DATA_VERSION + 1,
                EquipmentStorageFormat.EQUIPMENT_COMPOUND
        );

        assertFalse(legacy.supportsSafeEditing());
        assertTrue(modern.supportsSafeEditing());
        assertFalse(future.supportsSafeEditing());
    }

    private static OfflinePlayerData data(int dataVersion, EquipmentStorageFormat format) {
        return new OfflinePlayerData(
                UUID.randomUUID(),
                InventorySnapshot.empty(),
                new PlayerDataRevision("0".repeat(64)),
                dataVersion,
                format
        );
    }
}
