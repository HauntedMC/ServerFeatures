package nl.hauntedmc.serverfeatures.features.invtools.listener;

import nl.hauntedmc.serverfeatures.features.invtools.gui.InvToolsView;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

/**
 * Rolls back cross-inventory offline transfers and settles isolated cursor state before Paper starts
 * persisting the staff member.
 *
 * <p>This listener deliberately runs at LOWEST and is registered before the ordinary transfer
 * listener. Cursor-only sessions must also be handled here: waiting until PlayerQuitEvent can be too
 * late to restore a staff-owned cursor to the inventory that Paper is about to save.</p>
 */
public final class InvToolsTransferAbortListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getReason() != InventoryCloseEvent.Reason.DISCONNECT
                && event.getReason() != InventoryCloseEvent.Reason.DEATH) {
            return;
        }
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        InvToolsView view = holder(event.getView().getTopInventory());
        if (view == null || view.onlineSession()) {
            return;
        }
        view.abortOfflineTransfersForDisconnect();
    }

    private static InvToolsView holder(Inventory inventory) {
        return inventory != null && inventory.getHolder(false) instanceof InvToolsView view
                ? view
                : null;
    }
}
