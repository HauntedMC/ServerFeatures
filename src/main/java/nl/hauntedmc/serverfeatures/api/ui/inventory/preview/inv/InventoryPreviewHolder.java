package nl.hauntedmc.serverfeatures.api.ui.inventory.preview.inv;

import nl.hauntedmc.serverfeatures.api.ui.inventory.preview.PreviewHolder;
import org.bukkit.inventory.Inventory;

/**
 * Marker holder for inventory preview windows.
 */
public record InventoryPreviewHolder(InventorySnapshot snapshot) implements PreviewHolder {

    @Override
    public Inventory getInventory() {
        // Bukkit provides the inventory instance.
        return null;
    }
}
