package nl.hauntedmc.serverfeatures.features.autolapis.listener;

import nl.hauntedmc.serverfeatures.features.autolapis.AutoLapis;
import nl.hauntedmc.serverfeatures.features.autolapis.internal.AutoLapisHandler;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

public final class AutoLapisListener implements Listener {

    private final AutoLapis feature;
    private final AutoLapisHandler handler;

    public AutoLapisListener(AutoLapis feature, AutoLapisHandler handler) {
        this.feature = feature;
        this.handler = handler;
    }

    // --- Helpers
    private boolean isEnchanting(Inventory inv) {
        return inv instanceof EnchantingInventory;
    }

    private EnchantingInventory asEnchanting(Inventory inv) {
        return (EnchantingInventory) inv;
    }

    // Place (or top up) the marker when the GUI opens
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        Inventory inv = event.getInventory();
        HumanEntity viewer = event.getPlayer();
        if (!isEnchanting(inv) || !handler.eligible(viewer)) return;

        handler.ensureMarker(asEnchanting(inv));
    }

    // Keep marker present while selecting an item (updates the table display)
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepare(PrepareItemEnchantEvent event) {
        Inventory inv = event.getInventory();
        if (!handler.eligible(event.getEnchanter())) return;
        handler.ensureMarker(asEnchanting(inv));
    }

    // After enchanting, immediately restore the marker stack to full
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        Inventory inv = event.getInventory();
        if (!handler.eligible(event.getEnchanter())) return;

        // Restore next tick to let vanilla reduce lapis, then we refill.
        feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> handler.ensureMarker(asEnchanting(inv)));
    }

    // Prevent moving / grabbing the marker lapis via any click
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        InventoryView view = event.getView();
        Inventory top = view.getTopInventory();
        if (!isEnchanting(top)) return;
        if (!handler.eligible(event.getWhoClicked())) return;

        // If the current/dragged item is our marker, block it.
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        boolean involvesMarker = handler.isMarker(current) || handler.isMarker(cursor);
        if (involvesMarker) {
            event.setCancelled(true);
            // Also make sure marker is present (cursor could have tried to pick it up)
            handler.ensureMarker(asEnchanting(top));
        }
    }

    // Prevent dragging the marker out via drag events
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!isEnchanting(top)) return;
        if (!handler.eligible(event.getWhoClicked())) return;

        ItemStack oldCursor = event.getOldCursor();
        if (handler.isMarker(oldCursor)) {
            event.setCancelled(true);
            handler.ensureMarker(asEnchanting(top));
        }
    }

    // On close, ensure the marker is removed so it never drops to the ground or goes to the player
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        if (!isEnchanting(inv)) return;
        if (!handler.eligible(event.getPlayer())) return;
        handler.clearMarker(asEnchanting(inv));
    }
}
