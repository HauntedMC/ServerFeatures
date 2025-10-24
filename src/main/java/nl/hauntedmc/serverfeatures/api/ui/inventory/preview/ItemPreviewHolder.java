package nl.hauntedmc.serverfeatures.api.ui.inventory.preview;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Marker holder for preview windows so listeners can protect them.
 */
public final class ItemPreviewHolder implements InventoryHolder {
    private final ItemStack snapshot;

    public ItemPreviewHolder(ItemStack snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public Inventory getInventory() {
        return null; // Bukkit supplies Inventory instance; we just mark the holder type.
    }

    public ItemStack snapshot() {
        return snapshot;
    }
}
