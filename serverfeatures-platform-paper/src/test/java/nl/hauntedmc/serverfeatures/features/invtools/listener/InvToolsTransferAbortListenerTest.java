package nl.hauntedmc.serverfeatures.features.invtools.listener;

import nl.hauntedmc.serverfeatures.features.invtools.gui.InvToolsView;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvToolsTransferAbortListenerTest {

    @Test
    void disconnectRollsBackOfflineTransfersBeforeNormalCloseHandling() {
        InventoryCloseEvent event = closeEvent(InventoryCloseEvent.Reason.DISCONNECT, true);
        InvToolsView view = holder(event);

        new InvToolsTransferAbortListener().onInventoryClose(event);

        verify(view).abortOfflineTransfersForDisconnect();
    }

    @Test
    void deathRollsBackOfflineTransfersBeforeNormalCloseHandling() {
        InventoryCloseEvent event = closeEvent(InventoryCloseEvent.Reason.DEATH, true);
        InvToolsView view = holder(event);

        new InvToolsTransferAbortListener().onInventoryClose(event);

        verify(view).abortOfflineTransfersForDisconnect();
    }

    @Test
    void ordinaryCloseLeavesTransfersForAtomicSaveSettlement() {
        InventoryCloseEvent event = closeEvent(InventoryCloseEvent.Reason.PLAYER, true);
        InvToolsView view = holder(event);

        new InvToolsTransferAbortListener().onInventoryClose(event);

        verify(view, never()).abortOfflineTransfersForDisconnect();
    }

    @Test
    void disconnectWithoutCrossInventoryTransfersRequiresNoRollback() {
        InventoryCloseEvent event = closeEvent(InventoryCloseEvent.Reason.DISCONNECT, false);
        InvToolsView view = holder(event);

        new InvToolsTransferAbortListener().onInventoryClose(event);

        verify(view, never()).abortOfflineTransfersForDisconnect();
    }

    private static InventoryCloseEvent closeEvent(
            InventoryCloseEvent.Reason reason,
            boolean hasTransfers
    ) {
        InventoryCloseEvent event = mock(InventoryCloseEvent.class);
        Player player = mock(Player.class);
        InventoryView inventoryView = mock(InventoryView.class);
        Inventory topInventory = mock(Inventory.class);
        InvToolsView view = mock(InvToolsView.class);

        when(event.getReason()).thenReturn(reason);
        when(event.getPlayer()).thenReturn(player);
        when(event.getView()).thenReturn(inventoryView);
        when(inventoryView.getTopInventory()).thenReturn(topInventory);
        when(topInventory.getHolder(false)).thenReturn(view);
        when(view.onlineSession()).thenReturn(false);
        when(view.hasViewerTransfers()).thenReturn(hasTransfers);
        return event;
    }

    private static InvToolsView holder(InventoryCloseEvent event) {
        return (InvToolsView) event.getView().getTopInventory().getHolder(false);
    }
}
