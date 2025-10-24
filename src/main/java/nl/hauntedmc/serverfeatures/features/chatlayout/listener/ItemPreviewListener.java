package nl.hauntedmc.serverfeatures.features.chatlayout.listener;

import nl.hauntedmc.serverfeatures.api.ui.inventory.preview.ItemPreviewHolder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Cancels moving/taking items inside the item preview window.
 */
public final class ItemPreviewListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ItemPreviewHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ItemPreviewHolder) {
            event.setCancelled(true);
        }
    }
}
