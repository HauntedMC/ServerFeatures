package nl.hauntedmc.serverfeatures.api.ui.inventory.preview.item;

import com.mongodb.lang.Nullable;
import nl.hauntedmc.serverfeatures.api.ui.inventory.preview.PreviewHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Marker holder for preview windows so listeners can protect them.
 */
public final class ItemPreviewHolder implements PreviewHolder {
    private final ItemStack snapshot;

    public ItemPreviewHolder(ItemStack snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public Inventory getInventory() {
        // Bukkit supplies Inventory instance; we just mark the holder type.
        return null;
    }

    public ItemStack snapshot() {
        return snapshot;
    }
}
