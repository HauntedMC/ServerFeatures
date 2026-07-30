package nl.hauntedmc.serverfeatures.features.invtools.listener;

import nl.hauntedmc.serverfeatures.features.invtools.InvTools;
import nl.hauntedmc.serverfeatures.features.invtools.gui.InvToolsView;
import nl.hauntedmc.serverfeatures.features.invtools.gui.OfflineCursorTransaction;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventoryKind;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventorySnapshot;
import nl.hauntedmc.serverfeatures.features.invtools.service.InvToolsService;
import nl.hauntedmc.serverfeatures.framework.config.FeatureConfigHandler;
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

import java.util.Optional;
import java.util.UUID;

import static nl.hauntedmc.serverfeatures.features.invtools.support.TestItemStacks.item;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvToolsOfflineInteractionListenerTest {

    private final UUID viewerId = UUID.randomUUID();
    private InvTools feature;
    private Player viewer;
    private PlayerInventory viewerInventory;
    private Inventory topInventory;
    private InventoryView inventoryView;
    private InventoryClickEvent event;
    private InvToolsView view;
    private InvToolsOfflineInteractionListener listener;

    @BeforeEach
    void setUp() {
        feature = mock(InvTools.class);
        FeatureConfigHandler config = mock(FeatureConfigHandler.class);
        when(feature.getConfigHandler()).thenReturn(config);
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
        when(event.getCursor()).thenReturn(null);
        when(view.cursor()).thenReturn(null);

        listener = new InvToolsOfflineInteractionListener(feature);
    }

    @Test
    void directPickupFromViewerStorageRoutesThroughTheViewTransaction() {
        ItemStack[] viewerStorage = new ItemStack[InventorySnapshot.STORAGE_SIZE];
        viewerStorage[4] = item(Material.DIAMOND, 3);
        InventorySnapshot target = InventorySnapshot.empty();
        OfflineCursorTransaction transaction = new OfflineCursorTransaction();
        OfflineCursorTransaction.Plan plan = transaction.plan(
                OfflineCursorTransaction.Side.VIEWER,
                InventoryAction.PICKUP_ALL,
                viewerStorage[4]
        ).orElseThrow();
        when(view.snapshot()).thenReturn(target);
        when(viewerInventory.getStorageContents()).thenReturn(viewerStorage);
        when(event.getClickedInventory()).thenReturn(viewerInventory);
        when(event.getSlot()).thenReturn(4);
        when(event.getAction()).thenReturn(InventoryAction.PICKUP_ALL);
        when(view.planOfflineCursor(
                OfflineCursorTransaction.Side.VIEWER,
                InventoryAction.PICKUP_ALL,
                viewerStorage[4]
        )).thenReturn(Optional.of(plan));
        when(view.applyOfflineCursorMutation(any(), any(), any(), any())).thenReturn(true);
        when(view.cursor()).thenReturn(null, plan.result().cursorItem());

        listener.onInventoryClick(event);

        verify(event).setCancelled(true);
        ArgumentCaptor<ItemStack[]> changedViewer = ArgumentCaptor.forClass(ItemStack[].class);
        verify(view).applyOfflineCursorMutation(
                eq(plan),
                eq(target),
                eq(viewerStorage),
                changedViewer.capture()
        );
        assertNull(changedViewer.getValue()[4]);
        verify(viewer).setItemOnCursor(any(ItemStack.class));
    }

    @Test
    void directPlacementFromViewerCursorIntoTargetIsJournaledByTheView() {
        ItemStack carried = item(Material.EMERALD, 5);
        ItemStack[] viewerStorage = new ItemStack[InventorySnapshot.STORAGE_SIZE];
        InventorySnapshot target = InventorySnapshot.empty();
        OfflineCursorTransaction transaction = new OfflineCursorTransaction(
                carried,
                OfflineCursorTransaction.Side.VIEWER
        );
        OfflineCursorTransaction.Plan plan = transaction.plan(
                OfflineCursorTransaction.Side.TARGET,
                InventoryAction.PLACE_ALL,
                null
        ).orElseThrow();
        when(view.snapshot()).thenReturn(target);
        when(viewerInventory.getStorageContents()).thenReturn(viewerStorage);
        when(event.getClickedInventory()).thenReturn(topInventory);
        when(event.getSlot()).thenReturn(9);
        when(event.getAction()).thenReturn(InventoryAction.PLACE_ALL);
        when(event.getCursor()).thenReturn(carried);
        when(view.cursor()).thenReturn(carried, null);
        when(view.cursorOwner()).thenReturn(OfflineCursorTransaction.Side.VIEWER);
        when(view.planOfflineCursor(
                OfflineCursorTransaction.Side.TARGET,
                InventoryAction.PLACE_ALL,
                null
        )).thenReturn(Optional.of(plan));
        when(view.applyOfflineCursorMutation(any(), any(), any(), any())).thenReturn(true);

        listener.onInventoryClick(event);

        ArgumentCaptor<InventorySnapshot> changedTarget =
                ArgumentCaptor.forClass(InventorySnapshot.class);
        verify(view).applyOfflineCursorMutation(
                eq(plan),
                changedTarget.capture(),
                eq(viewerStorage),
                any(ItemStack[].class)
        );
        assertEquals(
                5,
                changedTarget.getValue().itemAt(InventoryKind.PLAYER, 9).getAmount()
        );
        assertFalse(plan.transfer().addedToViewer());
        assertEquals(5, plan.transfer().item().getAmount());
    }

    @Test
    void shiftClickIsLeftForTheDedicatedTransferListenerWhenCursorIsEmpty() {
        when(event.getAction()).thenReturn(InventoryAction.MOVE_TO_OTHER_INVENTORY);
        when(event.getClickedInventory()).thenReturn(viewerInventory);

        listener.onInventoryClick(event);

        verify(event, never()).setCancelled(true);
        verify(view, never()).applyOfflineCursorMutation(any(), any(), any(), any());
    }

    @Test
    void shiftClickIsBlockedWhileDirectCursorMovementIsUnresolved() {
        ItemStack carried = item(Material.STONE, 2);
        when(event.getAction()).thenReturn(InventoryAction.MOVE_TO_OTHER_INVENTORY);
        when(event.getClickedInventory()).thenReturn(viewerInventory);
        when(event.getCursor()).thenReturn(carried);
        when(view.cursor()).thenReturn(carried);

        listener.onInventoryClick(event);

        verify(event).setCancelled(true);
        verify(view, never()).applyOfflineCursorMutation(any(), any(), any(), any());
    }

    @Test
    void nonHelmetCannotBePlacedIntoTheTargetHelmetSlot() {
        ItemStack carried = item(Material.STONE);
        ItemStack[] viewerStorage = new ItemStack[InventorySnapshot.STORAGE_SIZE];
        InventorySnapshot target = InventorySnapshot.empty();
        OfflineCursorTransaction transaction = new OfflineCursorTransaction(
                carried,
                OfflineCursorTransaction.Side.VIEWER
        );
        OfflineCursorTransaction.Plan plan = transaction.plan(
                OfflineCursorTransaction.Side.TARGET,
                InventoryAction.PLACE_ALL,
                null
        ).orElseThrow();
        when(view.snapshot()).thenReturn(target);
        when(viewerInventory.getStorageContents()).thenReturn(viewerStorage);
        when(event.getClickedInventory()).thenReturn(topInventory);
        when(event.getSlot()).thenReturn(0);
        when(event.getAction()).thenReturn(InventoryAction.PLACE_ALL);
        when(event.getCursor()).thenReturn(carried);
        when(view.cursor()).thenReturn(carried);
        when(view.planOfflineCursor(
                OfflineCursorTransaction.Side.TARGET,
                InventoryAction.PLACE_ALL,
                null
        )).thenReturn(Optional.of(plan));

        listener.onInventoryClick(event);

        verify(event).setCancelled(true);
        verify(view, never()).applyOfflineCursorMutation(any(), any(), any(), any());
    }
}
