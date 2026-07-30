package nl.hauntedmc.serverfeatures.features.invtools.listener;

import nl.hauntedmc.serverfeatures.features.invtools.InvTools;
import nl.hauntedmc.serverfeatures.features.invtools.gui.InvToolsView;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventoryKind;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventorySnapshot;
import nl.hauntedmc.serverfeatures.features.invtools.service.InvToolsService;
import nl.hauntedmc.serverfeatures.framework.config.FeatureConfigHandler;
import nl.hauntedmc.serverfeatures.framework.log.FeatureLogger;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static nl.hauntedmc.serverfeatures.features.invtools.support.TestItemStacks.item;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvToolsTransferListenerTest {

    private final UUID viewerId = UUID.randomUUID();
    private InvTools feature;
    private Player viewer;
    private PlayerInventory viewerInventory;
    private Inventory topInventory;
    private InventoryView inventoryView;
    private InventoryClickEvent event;
    private InvToolsView view;
    private InvToolsTransferListener listener;

    @BeforeEach
    void setUp() {
        feature = mock(InvTools.class);
        FeatureConfigHandler config = mock(FeatureConfigHandler.class);
        when(feature.getConfigHandler()).thenReturn(config);
        when(feature.getLogger()).thenReturn(mock(FeatureLogger.class));
        when(config.get("audit_edits", Boolean.class, true)).thenReturn(false);

        viewer = mock(Player.class);
        viewerInventory = mock(PlayerInventory.class);
        topInventory = mock(Inventory.class);
        inventoryView = mock(InventoryView.class);
        event = mock(InventoryClickEvent.class);
        view = mock(InvToolsView.class);

        when(viewer.getUniqueId()).thenReturn(viewerId);
        when(viewer.getInventory()).thenReturn(viewerInventory);
        when(viewer.hasPermission(InvToolsService.openEditPermission(InventoryKind.PLAYER)))
                .thenReturn(true);
        when(view.viewerId()).thenReturn(viewerId);
        when(view.kind()).thenReturn(InventoryKind.PLAYER);
        when(view.editable()).thenReturn(true);
        when(view.isInteractive()).thenReturn(true);
        when(view.onlineSession()).thenReturn(false);
        when(view.owns(topInventory)).thenReturn(true);
        when(topInventory.getHolder(false)).thenReturn(view);
        when(inventoryView.getTopInventory()).thenReturn(topInventory);
        when(event.getView()).thenReturn(inventoryView);
        when(event.getWhoClicked()).thenReturn(viewer);
        when(event.getAction()).thenReturn(InventoryAction.MOVE_TO_OTHER_INVENTORY);
        when(view.applyOfflineShiftTransfer(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(true);

        listener = new InvToolsTransferListener(feature);
    }

    @Test
    void shiftClickFromOfflineTargetMovesTheStackIntoStaffStorage() {
        InventorySnapshot target = InventorySnapshot.empty()
                .withBackingSlot(InventoryKind.PLAYER, 9, item(Material.DIAMOND, 3));
        ItemStack[] viewerStorage = new ItemStack[InventorySnapshot.STORAGE_SIZE];
        when(view.snapshot()).thenReturn(target);
        when(viewerInventory.getStorageContents()).thenReturn(viewerStorage);
        when(event.getClickedInventory()).thenReturn(topInventory);
        when(event.getSlot()).thenReturn(9);

        listener.onInventoryClick(event);

        verify(event).setCancelled(true);
        ArgumentCaptor<InventorySnapshot> targetCaptor =
                ArgumentCaptor.forClass(InventorySnapshot.class);
        ArgumentCaptor<ItemStack[]> viewerCaptor = ArgumentCaptor.forClass(ItemStack[].class);
        ArgumentCaptor<ItemStack> transferredCaptor = ArgumentCaptor.forClass(ItemStack.class);
        verify(view).applyOfflineShiftTransfer(
                targetCaptor.capture(),
                eq(viewerStorage),
                viewerCaptor.capture(),
                transferredCaptor.capture(),
                eq(true)
        );
        assertNull(targetCaptor.getValue().itemAt(InventoryKind.PLAYER, 9));
        assertEquals(3, viewerCaptor.getValue()[9].getAmount());
        assertEquals(3, transferredCaptor.getValue().getAmount());
    }

    @Test
    void shiftClickFromStaffStorageMovesTheStackIntoOfflineTarget() {
        InventorySnapshot target = InventorySnapshot.empty();
        ItemStack[] viewerStorage = new ItemStack[InventorySnapshot.STORAGE_SIZE];
        viewerStorage[4] = item(Material.EMERALD, 5);
        when(view.snapshot()).thenReturn(target);
        when(view.cursor()).thenReturn(null);
        when(viewerInventory.getStorageContents()).thenReturn(viewerStorage);
        when(event.getClickedInventory()).thenReturn(viewerInventory);
        when(event.getSlot()).thenReturn(4);

        listener.onInventoryClick(event);

        verify(event).setCancelled(true);
        ArgumentCaptor<InventorySnapshot> targetCaptor =
                ArgumentCaptor.forClass(InventorySnapshot.class);
        ArgumentCaptor<ItemStack[]> viewerCaptor = ArgumentCaptor.forClass(ItemStack[].class);
        ArgumentCaptor<ItemStack> transferredCaptor = ArgumentCaptor.forClass(ItemStack.class);
        verify(view).applyOfflineShiftTransfer(
                targetCaptor.capture(),
                eq(viewerStorage),
                viewerCaptor.capture(),
                transferredCaptor.capture(),
                eq(false)
        );
        assertEquals(5, targetCaptor.getValue().itemAt(InventoryKind.PLAYER, 9).getAmount());
        assertNull(viewerCaptor.getValue()[4]);
        assertEquals(5, transferredCaptor.getValue().getAmount());
    }
}
