package nl.hauntedmc.serverfeatures.api.ui.inventory.preview.inv;

import nl.hauntedmc.serverfeatures.api.ui.inventory.preview.PreviewHolder;
import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryPreviewHolderTest {

    @Test
    void supportsNullSnapshotAndUsesNullInventoryMarker() {
        InventoryPreviewHolder holder = new InventoryPreviewHolder(null);

        assertNull(holder.snapshot());
        assertNull(holder.getInventory());
    }

    @Test
    void retainsProvidedSnapshotAndImplementsPreviewMarker() {
        PlayerInventory inventory = InterfaceProxy.of(PlayerInventory.class, Map.of(
                "getItem", args -> null,
                "getHelmet", args -> null,
                "getChestplate", args -> null,
                "getLeggings", args -> null,
                "getBoots", args -> null,
                "getItemInOffHand", args -> null
        ));
        Player player = InterfaceProxy.of(Player.class, Map.of(
                "getInventory", args -> inventory,
                "getName", args -> "Remy"
        ));
        InventorySnapshot snapshot = InventorySnapshot.from(player);

        InventoryPreviewHolder holder = new InventoryPreviewHolder(snapshot);

        assertSame(snapshot, holder.snapshot());
        assertTrue(holder instanceof PreviewHolder);
        assertNull(holder.getInventory());
    }
}
