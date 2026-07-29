package nl.hauntedmc.serverfeatures.features.invtools.gui;

import nl.hauntedmc.serverfeatures.features.invtools.model.InventorySnapshot;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static nl.hauntedmc.serverfeatures.features.invtools.support.TestItemStacks.item;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerStorageTransferTest {

    @Test
    void insertionMergesInMainStorageBeforeUsingAnEmptySlot() {
        ItemStack[] storage = new ItemStack[InventorySnapshot.STORAGE_SIZE];
        storage[9] = item(Material.DIAMOND, 63);

        PlayerStorageTransfer.InsertionResult result = PlayerStorageTransfer.insert(
                storage,
                item(Material.DIAMOND, 3)
        );

        assertNull(result.remainder());
        assertEquals(64, result.storage()[9].getAmount());
        assertEquals(2, result.storage()[10].getAmount());
        assertEquals(63, storage[9].getAmount());
    }

    @Test
    void insertionUsesMainStorageBeforeHotbar() {
        PlayerStorageTransfer.InsertionResult result = PlayerStorageTransfer.insert(
                new ItemStack[InventorySnapshot.STORAGE_SIZE],
                item(Material.EMERALD, 4)
        );

        assertEquals(4, result.storage()[9].getAmount());
        assertNull(result.storage()[0]);
    }

    @Test
    void insertionReturnsRemainderWhenStorageIsFull() {
        ItemStack[] storage = new ItemStack[InventorySnapshot.STORAGE_SIZE];
        for (int slot = 0; slot < storage.length; slot++) {
            storage[slot] = item(Material.STONE, 64);
        }

        PlayerStorageTransfer.InsertionResult result = PlayerStorageTransfer.insert(
                storage,
                item(Material.DIAMOND, 2)
        );

        assertEquals(2, result.remainder().getAmount());
    }

    @Test
    void removalFindsMatchingStacksAcrossStorage() {
        ItemStack[] storage = new ItemStack[InventorySnapshot.STORAGE_SIZE];
        storage[9] = item(Material.DIAMOND, 2);
        storage[10] = item(Material.DIAMOND, 3);

        PlayerStorageTransfer.RemovalResult result = PlayerStorageTransfer.removeMatching(
                storage,
                item(Material.DIAMOND, 4)
        );

        assertNull(result.remainder());
        assertNull(result.storage()[9]);
        assertEquals(1, result.storage()[10].getAmount());
    }

    @Test
    void decrementRejectsAnInvalidAmount() {
        ItemStack[] storage = new ItemStack[InventorySnapshot.STORAGE_SIZE];
        storage[4] = item(Material.DIAMOND, 2);

        assertThrows(
                IllegalArgumentException.class,
                () -> PlayerStorageTransfer.decrementSlot(storage, 4, 3)
        );
    }

    @Test
    void contentsComparisonUsesAmountAndSimilarity() {
        ItemStack[] first = new ItemStack[InventorySnapshot.STORAGE_SIZE];
        ItemStack[] second = new ItemStack[InventorySnapshot.STORAGE_SIZE];
        first[7] = item(Material.DIAMOND, 2);
        second[7] = item(Material.DIAMOND, 2);

        assertTrue(PlayerStorageTransfer.sameContents(first, second));
        second[7].setAmount(1);
        assertEquals(false, PlayerStorageTransfer.sameContents(first, second));
    }
}
