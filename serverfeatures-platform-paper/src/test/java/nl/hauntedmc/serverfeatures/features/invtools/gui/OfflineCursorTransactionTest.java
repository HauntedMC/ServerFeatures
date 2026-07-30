package nl.hauntedmc.serverfeatures.features.invtools.gui;

import nl.hauntedmc.serverfeatures.features.invtools.model.InventorySnapshot;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryAction;
import org.junit.jupiter.api.Test;

import static nl.hauntedmc.serverfeatures.features.invtools.support.TestItemStacks.item;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineCursorTransactionTest {

    @Test
    void viewerItemIsJournaledOnlyWhenPlacedIntoTheTarget() {
        OfflineCursorTransaction transaction = new OfflineCursorTransaction();

        OfflineCursorTransaction.Plan pickup = transaction.plan(
                OfflineCursorTransaction.Side.VIEWER,
                InventoryAction.PICKUP_ALL,
                item(Material.DIAMOND, 4)
        ).orElseThrow();
        assertNull(pickup.transfer());
        transaction.commit(pickup);

        OfflineCursorTransaction.Plan place = transaction.plan(
                OfflineCursorTransaction.Side.TARGET,
                InventoryAction.PLACE_ALL,
                null
        ).orElseThrow();

        assertFalse(place.transfer().addedToViewer());
        assertEquals(4, place.transfer().item().getAmount());
        transaction.commit(place);
        assertFalse(transaction.hasCursor());
    }

    @Test
    void targetItemIsJournaledWhenPlacedIntoTheViewerInventory() {
        OfflineCursorTransaction transaction = new OfflineCursorTransaction();
        transaction.commit(transaction.plan(
                OfflineCursorTransaction.Side.TARGET,
                InventoryAction.PICKUP_ALL,
                item(Material.EMERALD, 3)
        ).orElseThrow());

        OfflineCursorTransaction.Plan place = transaction.plan(
                OfflineCursorTransaction.Side.VIEWER,
                InventoryAction.PLACE_ALL,
                null
        ).orElseThrow();

        assertTrue(place.transfer().addedToViewer());
        assertEquals(3, place.transfer().item().getAmount());
    }

    @Test
    void sameSideRearrangementDoesNotCreateATransfer() {
        OfflineCursorTransaction transaction = new OfflineCursorTransaction();
        transaction.commit(transaction.plan(
                OfflineCursorTransaction.Side.VIEWER,
                InventoryAction.PICKUP_HALF,
                item(Material.STONE, 5)
        ).orElseThrow());

        OfflineCursorTransaction.Plan place = transaction.plan(
                OfflineCursorTransaction.Side.VIEWER,
                InventoryAction.PLACE_ONE,
                null
        ).orElseThrow();

        assertNull(place.transfer());
        assertEquals(2, place.result().cursorItem().getAmount());
    }

    @Test
    void partialSameSidePlacementPreservesThePreferredReturnSlot() {
        OfflineCursorTransaction transaction = new OfflineCursorTransaction();
        OfflineCursorTransaction.Plan pickup = transaction.plan(
                OfflineCursorTransaction.Side.TARGET,
                InventorySnapshot.HELMET_SLOT,
                InventoryAction.PICKUP_ALL,
                item(Material.CARVED_PUMPKIN, 3)
        ).orElseThrow();
        transaction.commit(pickup);

        assertEquals(InventorySnapshot.HELMET_SLOT, transaction.preferredReturnSlot());

        OfflineCursorTransaction.Plan placement = transaction.plan(
                OfflineCursorTransaction.Side.TARGET,
                9,
                InventoryAction.PLACE_ONE,
                null
        ).orElseThrow();
        transaction.commit(placement);

        assertEquals(2, transaction.cursor().getAmount());
        assertEquals(InventorySnapshot.HELMET_SLOT, transaction.preferredReturnSlot());
    }

    @Test
    void crossInventorySwapTransfersThePlacedStackAndChangesCursorCustody() {
        OfflineCursorTransaction transaction = new OfflineCursorTransaction(
                item(Material.DIAMOND, 2),
                OfflineCursorTransaction.Side.VIEWER
        );

        OfflineCursorTransaction.Plan swap = transaction.plan(
                OfflineCursorTransaction.Side.TARGET,
                14,
                InventoryAction.SWAP_WITH_CURSOR,
                item(Material.EMERALD, 5)
        ).orElseThrow();

        assertFalse(swap.transfer().addedToViewer());
        assertEquals(Material.DIAMOND, swap.transfer().item().getType());
        assertEquals(OfflineCursorTransaction.Side.TARGET, swap.nextOwner());
        assertEquals(14, swap.nextReturnSlot());
        assertEquals(Material.EMERALD, swap.result().cursorItem().getType());
    }

    @Test
    void rejectsCombiningAnExistingCursorWithTheOtherPersistenceDomain() {
        OfflineCursorTransaction transaction = new OfflineCursorTransaction(
                item(Material.STONE, 10),
                OfflineCursorTransaction.Side.VIEWER
        );

        assertTrue(transaction.plan(
                OfflineCursorTransaction.Side.TARGET,
                InventoryAction.PICKUP_SOME,
                item(Material.STONE, 8)
        ).isEmpty());
    }

    @Test
    void partialCrossPlacementJournalsOnlyTheTransferredAmount() {
        OfflineCursorTransaction transaction = new OfflineCursorTransaction(
                item(Material.STONE, 10),
                OfflineCursorTransaction.Side.VIEWER
        );

        OfflineCursorTransaction.Plan plan = transaction.plan(
                OfflineCursorTransaction.Side.TARGET,
                InventoryAction.PLACE_SOME,
                item(Material.STONE, 60)
        ).orElseThrow();

        assertEquals(4, plan.transfer().item().getAmount());
        assertEquals(6, plan.result().cursorItem().getAmount());
        assertEquals(OfflineCursorTransaction.Side.VIEWER, plan.nextOwner());
    }
}
