package nl.hauntedmc.serverfeatures.features.invtools.gui;

import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static nl.hauntedmc.serverfeatures.features.invtools.support.TestItemStacks.item;

class InventoryClickMutationTest {

    @Test
    void rightClickPlacesOneItemIntoAnEmptyMappedSlot() {
        ItemStack cursor = item(Material.DIAMOND, 4);

        InventoryClickMutation.Result result = InventoryClickMutation.apply(
                InventoryAction.PLACE_ONE,
                null,
                cursor
        ).orElseThrow();

        assertEquals(1, result.slotItem().getAmount());
        assertEquals(3, result.cursorItem().getAmount());
        assertEquals(4, cursor.getAmount());
    }

    @Test
    void rightClickPicksUpTheLargerHalfOfAnOddStack() {
        InventoryClickMutation.Result result = InventoryClickMutation.apply(
                InventoryAction.PICKUP_HALF,
                item(Material.EMERALD, 5),
                null
        ).orElseThrow();

        assertEquals(2, result.slotItem().getAmount());
        assertEquals(3, result.cursorItem().getAmount());
    }

    @Test
    void leftClickSwapsMappedSlotAndCursor() {
        ItemStack stone = item(Material.STONE, 8);
        ItemStack dirt = item(Material.DIRT, 2);
        InventoryClickMutation.Result result = InventoryClickMutation.apply(
                InventoryAction.SWAP_WITH_CURSOR,
                stone,
                dirt
        ).orElseThrow();

        assertEquals(dirt.getType(), result.slotItem().getType());
        assertEquals(2, result.slotItem().getAmount());
        assertEquals(stone.getType(), result.cursorItem().getType());
        assertEquals(8, result.cursorItem().getAmount());
    }

    @Test
    void takingAllClearsMappedSlot() {
        InventoryClickMutation.Result result = InventoryClickMutation.apply(
                InventoryAction.PICKUP_ALL,
                item(Material.GOLD_INGOT, 7),
                null
        ).orElseThrow();

        assertNull(result.slotItem());
        assertEquals(7, result.cursorItem().getAmount());
    }

    @Test
    void takingAllMergesWithAnExistingSimilarCursorStack() {
        InventoryClickMutation.Result result = InventoryClickMutation.apply(
                InventoryAction.PICKUP_ALL,
                item(Material.GOLD_INGOT, 7),
                item(Material.GOLD_INGOT, 3)
        ).orElseThrow();

        assertNull(result.slotItem());
        assertEquals(10, result.cursorItem().getAmount());
    }

    @Test
    void pickupSomeTransfersOnlyTheSpaceRemainingOnTheCursor() {
        InventoryClickMutation.Result result = InventoryClickMutation.apply(
                InventoryAction.PICKUP_SOME,
                item(Material.GOLD_INGOT, 7),
                item(Material.GOLD_INGOT, 60)
        ).orElseThrow();

        assertEquals(3, result.slotItem().getAmount());
        assertEquals(64, result.cursorItem().getAmount());
    }

    @Test
    void placingAllMergesWithAnExistingSimilarSlotStack() {
        InventoryClickMutation.Result result = InventoryClickMutation.apply(
                InventoryAction.PLACE_ALL,
                item(Material.DIAMOND, 4),
                item(Material.DIAMOND, 3)
        ).orElseThrow();

        assertEquals(7, result.slotItem().getAmount());
        assertNull(result.cursorItem());
    }

    @Test
    void placeSomeTransfersOnlyTheSpaceRemainingInTheSlot() {
        InventoryClickMutation.Result result = InventoryClickMutation.apply(
                InventoryAction.PLACE_SOME,
                item(Material.DIAMOND, 62),
                item(Material.DIAMOND, 5)
        ).orElseThrow();

        assertEquals(64, result.slotItem().getAmount());
        assertEquals(3, result.cursorItem().getAmount());
    }

    @Test
    void rejectsBulkRoutingActions() {
        assertTrue(InventoryClickMutation.apply(
                InventoryAction.MOVE_TO_OTHER_INVENTORY,
                item(Material.STONE),
                null
        ).isEmpty());
        assertTrue(InventoryClickMutation.apply(
                InventoryAction.COLLECT_TO_CURSOR,
                item(Material.STONE),
                item(Material.STONE)
        ).isEmpty());
    }

    @Test
    void rejectsDestructiveDropActions() {
        assertTrue(InventoryClickMutation.apply(
                InventoryAction.DROP_ONE_SLOT,
                item(Material.DIAMOND, 3),
                null
        ).isEmpty());
        assertTrue(InventoryClickMutation.apply(
                InventoryAction.DROP_ALL_SLOT,
                item(Material.EMERALD, 2),
                null
        ).isEmpty());
    }

    @Test
    void rejectsMergesThatExceedTheMaterialStackLimit() {
        assertTrue(InventoryClickMutation.apply(
                InventoryAction.PICKUP_ALL,
                item(Material.STONE, 2),
                item(Material.STONE, 63)
        ).isEmpty());
        assertTrue(InventoryClickMutation.apply(
                InventoryAction.PLACE_ALL,
                item(Material.STONE, 63),
                item(Material.STONE, 2)
        ).isEmpty());
    }
}
