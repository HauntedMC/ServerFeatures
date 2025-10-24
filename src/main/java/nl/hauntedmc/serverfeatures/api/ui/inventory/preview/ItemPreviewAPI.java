package nl.hauntedmc.serverfeatures.api.ui.inventory.preview;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.entity.Player;

/**
 * Simple API for opening small preview GUIs.
 */
public final class ItemPreviewAPI {

    private ItemPreviewAPI() {}

    /**
     * Opens a 3x3 preview window (Dispenser-style) with a snapshot of the item in the center slot.
     * Call is automatically scheduled on the main server thread.
     *
     * @param plugin the plugin instance
     * @param viewer the player to show the preview to
     * @param snapshot the item to preview (cloned internally)
     * @param title the inventory title (adventure Component)
     */
    public static void open3x3Preview(Plugin plugin, Player viewer, ItemStack snapshot, Component title) {
        if (viewer == null || plugin == null) return;

        final ItemStack copy = (snapshot == null || snapshot.getType().isAir()) ? null : snapshot.clone();
        final Inventory inv = Bukkit.createInventory(new ItemPreviewHolder(copy), InventoryType.DISPENSER, title);

        // Put the item in the middle only if it's non-air
        if (copy != null) {
            inv.setItem(4, copy);
        }

        Bukkit.getScheduler().runTask(plugin, () -> viewer.openInventory(inv));
    }
}
