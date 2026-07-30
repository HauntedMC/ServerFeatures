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
    void disconnectSettlesOfflineStateBeforeNormalCloseHandling() {
        InventoryCloseEvent event = closeEvent(InventoryCloseEvent.Reason.DISCONNECT);
        InvToolsView view = holder(event);

        new InvToolsTransferAbortListener().onInventoryClose(event);

        verify(view).abortOfflineTransfersForDisconnect();
    }

    @Test
    void deathSettlesOfflineStateBeforeNormalCloseHandling() {
        InventoryCloseEvent event = closeEvent(InventoryCloseEvent.Reason.DEATH);
        InvToolsView view = holder(event);

        new InvToolsTransferAbortListener().onInventoryClose(event);

        verify(view).abortOfflineTransfersForDisconnect();
    }

    @Test
    void cursorOnlyDisconnectStillInvokesEarlySettlement() {
        InventoryCloseEvent event = closeEvent(InventoryCloseEvent.Reason.DISCONNECT);
        InvToolsView view = holder(event);
        when(view.hasViewerTransfers()).thenReturn(false);

        new InvToolsTransferAbortListener().onInventoryClose(event);

        verify(view).abortOfflineTransfersForDisconnect();
    }

    @Test
    void ordinaryCloseLeavesStateForAtomicSaveSettlement() {
        InventoryCloseEvent event = closeEvent(InventoryCloseEvent.Reason.PLAYER);
        InvToolsView view = holder(event);

        new InvToolsTransferAbortListener().onInventoryClose(event);

        verify(view, never()).abortOfflineTransfersForDisconnect();
    }

    private static InventoryCloseEvent closeEvent(InventoryCloseEvent.Reason reason) {
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
        return event;
    }

    private static InvToolsView holder(InventoryCloseEvent event) {
        return (InvToolsView) event.getView().getTopInventory().getHolder(false);
    }
}
