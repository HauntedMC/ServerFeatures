package nl.hauntedmc.serverfeatures.api.ui.inventory.preview.inv;

import nl.hauntedmc.serverfeatures.api.ui.inventory.preview.PreviewHolder;
import org.bukkit.inventory.Inventory;

/**
 * Marker holder for inventory preview windows.
 */
public final class InventoryPreviewHolder implements PreviewHolder {

    private final InventorySnapshot snapshot;

    public InventoryPreviewHolder(InventorySnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public Inventory getInventory() {
        // Bukkit provides the inventory instance.
        return null;
    }

    public InventorySnapshot snapshot() {
        return snapshot;
    }
}
