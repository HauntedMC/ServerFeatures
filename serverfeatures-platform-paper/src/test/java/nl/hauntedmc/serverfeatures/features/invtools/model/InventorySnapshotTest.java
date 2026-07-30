package nl.hauntedmc.serverfeatures.features.invtools.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static nl.hauntedmc.serverfeatures.features.invtools.support.TestItemStacks.item;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventorySnapshotTest {

    @Test
    void snapshotClonesItemsAtConstructionAndReadBoundaries() {
        ItemStack original = item(Material.DIAMOND, 3);
        ItemStack[] storage = new ItemStack[InventorySnapshot.STORAGE_SIZE];
        storage[7] = original;

        InventorySnapshot snapshot = new InventorySnapshot(
                storage,
                null,
                null,
                null,
                null,
                null,
                new ItemStack[InventorySnapshot.ENDER_CHEST_SIZE]
        );
        original.setAmount(1);
        ItemStack firstRead = snapshot.itemAt(InventoryKind.PLAYER, 7);
        firstRead.setAmount(2);
        ItemStack secondRead = snapshot.itemAt(InventoryKind.PLAYER, 7);

        assertEquals(3, secondRead.getAmount());
        assertNotSame(firstRead, secondRead);
    }

    @Test
    void writesArmorOffhandAndEnderSlotsWithoutChangingOriginalSnapshot() {
        InventorySnapshot empty = InventorySnapshot.empty();
        ItemStack helmet = item(Material.DIAMOND_HELMET);
        ItemStack shield = item(Material.SHIELD);
        ItemStack pearl = item(Material.ENDER_PEARL, 4);

        InventorySnapshot changed = empty
                .withBackingSlot(InventoryKind.PLAYER, 103, helmet)
                .withBackingSlot(InventoryKind.PLAYER, -106, shield)
                .withBackingSlot(InventoryKind.ENDER_CHEST, 26, pearl);

        assertNull(empty.helmet());
        assertNull(empty.offHand());
        assertNull(empty.itemAt(InventoryKind.ENDER_CHEST, 26));
        assertEquals(helmet.getType(), changed.helmet().getType());
        assertEquals(shield.getType(), changed.offHand().getType());
        assertEquals(4, changed.itemAt(InventoryKind.ENDER_CHEST, 26).getAmount());
    }

    @Test
    void rejectsUnsupportedBackingSlots() {
        InventorySnapshot snapshot = InventorySnapshot.empty();

        assertThrows(
                IllegalArgumentException.class,
                () -> snapshot.withBackingSlot(InventoryKind.PLAYER, 99, null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> snapshot.itemAt(InventoryKind.ENDER_CHEST, 27)
        );
    }

    @Test
    void insertionMergesThenUsesAnEmptySlotWithoutMutatingOriginal() {
        InventorySnapshot original = InventorySnapshot.empty()
                .withBackingSlot(InventoryKind.PLAYER, 9, item(Material.DIAMOND, 63));

        InventorySnapshot.InsertionResult result = original.insert(
                InventoryKind.PLAYER,
                item(Material.DIAMOND, 3)
        );

        assertNull(result.remainder());
        assertEquals(64, result.snapshot().itemAt(InventoryKind.PLAYER, 9).getAmount());
        assertEquals(2, result.snapshot().itemAt(InventoryKind.PLAYER, 0).getAmount());
        assertEquals(63, original.itemAt(InventoryKind.PLAYER, 9).getAmount());
        assertNull(original.itemAt(InventoryKind.PLAYER, 0));
    }

    @Test
    void shiftInsertionUsesMainStorageBeforeTheHotbar() {
        InventorySnapshot.InsertionResult result = InventorySnapshot.empty().shiftInsert(
                InventoryKind.PLAYER,
                item(Material.DIAMOND, 3)
        );

        assertNull(result.remainder());
        assertEquals(3, result.snapshot().itemAt(InventoryKind.PLAYER, 9).getAmount());
        assertNull(result.snapshot().itemAt(InventoryKind.PLAYER, 0));
        assertNull(result.snapshot().helmet());
    }

    @Test
    void shiftInsertionEquipsCompatibleArmorWhenItsSlotIsEmpty() {
        InventorySnapshot snapshot = InventorySnapshot.empty();
        ItemStack chestplate = item(Material.DIAMOND_CHESTPLATE);

        InventorySnapshot.InsertionResult result = snapshot.shiftInsert(
                InventoryKind.PLAYER,
                chestplate
        );

        assertNull(result.remainder());
        assertTrue(result.snapshot().chestplate().isSimilar(chestplate));
        assertNull(result.snapshot().itemAt(InventoryKind.PLAYER, 9));
    }

    @Test
    void shiftInsertionStoresArmorWhenItsMatchingSlotIsOccupied() {
        ItemStack equipped = item(Material.IRON_CHESTPLATE);
        ItemStack offered = item(Material.DIAMOND_CHESTPLATE);
        InventorySnapshot snapshot = InventorySnapshot.empty().withBackingSlot(
                InventoryKind.PLAYER,
                InventorySnapshot.CHESTPLATE_SLOT,
                equipped
        );

        InventorySnapshot.InsertionResult result = snapshot.shiftInsert(
                InventoryKind.PLAYER,
                offered
        );

        assertNull(result.remainder());
        assertTrue(result.snapshot().chestplate().isSimilar(equipped));
        assertTrue(result.snapshot().itemAt(InventoryKind.PLAYER, 9).isSimilar(offered));
    }

    @Test
    void shiftInsertionEquipsOneWearableItemAndStoresAnyStackRemainder() {
        ItemStack stackedHelmet = item(Material.CARVED_PUMPKIN, 3);

        InventorySnapshot.InsertionResult result = InventorySnapshot.empty().shiftInsert(
                InventoryKind.PLAYER,
                stackedHelmet
        );

        assertNull(result.remainder());
        assertEquals(1, result.snapshot().helmet().getAmount());
        assertEquals(2, result.snapshot().itemAt(InventoryKind.PLAYER, 9).getAmount());
    }

    @Test
    void nonEquipmentShiftInsertionNeverUsesTheArmorRow() {
        InventorySnapshot snapshot = InventorySnapshot.empty();

        InventorySnapshot.InsertionResult result = snapshot.shiftInsert(
                InventoryKind.PLAYER,
                item(Material.STONE, 4)
        );

        assertNull(result.remainder());
        assertEquals(4, result.snapshot().itemAt(InventoryKind.PLAYER, 9).getAmount());
        assertNull(result.snapshot().helmet());
        assertNull(result.snapshot().chestplate());
        assertNull(result.snapshot().leggings());
        assertNull(result.snapshot().boots());
    }

    @Test
    void shiftInsertionUsesAllEnderChestStorageSlots() {
        int stackCapacity = item(Material.DIAMOND).getMaxStackSize();
        InventorySnapshot snapshot = InventorySnapshot.empty()
                .withBackingSlot(
                        InventoryKind.ENDER_CHEST,
                        0,
                        item(Material.DIAMOND, stackCapacity - 1)
                );

        InventorySnapshot.InsertionResult result = snapshot.shiftInsert(
                InventoryKind.ENDER_CHEST,
                item(Material.DIAMOND, 3)
        );

        assertNull(result.remainder());
        assertEquals(
                stackCapacity,
                result.snapshot().itemAt(InventoryKind.ENDER_CHEST, 0).getAmount()
        );
        assertEquals(2, result.snapshot().itemAt(InventoryKind.ENDER_CHEST, 1).getAmount());
    }

    @Test
    void insertionReturnsARemainderWhenEveryTargetSlotIsFull() {
        InventorySnapshot full = InventorySnapshot.empty();
        for (int slot = 0; slot < InventorySnapshot.STORAGE_SIZE; slot++) {
            full = full.withBackingSlot(
                    InventoryKind.PLAYER,
                    slot,
                    item(Material.STONE, 64)
            );
        }
        full = full
                .withBackingSlot(
                        InventoryKind.PLAYER,
                        InventorySnapshot.HELMET_SLOT,
                        item(Material.STONE, 64)
                )
                .withBackingSlot(
                        InventoryKind.PLAYER,
                        InventorySnapshot.CHESTPLATE_SLOT,
                        item(Material.STONE, 64)
                )
                .withBackingSlot(
                        InventoryKind.PLAYER,
                        InventorySnapshot.LEGGINGS_SLOT,
                        item(Material.STONE, 64)
                )
                .withBackingSlot(
                        InventoryKind.PLAYER,
                        InventorySnapshot.BOOTS_SLOT,
                        item(Material.STONE, 64)
                )
                .withBackingSlot(
                        InventoryKind.PLAYER,
                        InventorySnapshot.OFF_HAND_SLOT,
                        item(Material.STONE, 64)
                );

        InventorySnapshot.InsertionResult result = full.insert(
                InventoryKind.PLAYER,
                item(Material.DIAMOND)
        );

        assertTrue(result.remainder().isSimilar(item(Material.DIAMOND)));
        assertEquals(1, result.remainder().getAmount());
    }

    @Test
    void insertionRestoresArmorToACompatibleSlotWhenStorageIsFull() {
        InventorySnapshot snapshot = InventorySnapshot.empty();
        for (int slot = 0; slot < InventorySnapshot.STORAGE_SIZE; slot++) {
            snapshot = snapshot.withBackingSlot(
                    InventoryKind.PLAYER,
                    slot,
                    item(Material.STONE, 64)
            );
        }
        ItemStack chestplate = item(Material.DIAMOND_CHESTPLATE);

        InventorySnapshot.InsertionResult result = snapshot.insert(
                InventoryKind.PLAYER,
                chestplate
        );

        assertNull(result.remainder());
        assertNull(result.snapshot().helmet());
        assertTrue(result.snapshot().chestplate().isSimilar(chestplate));
    }

    @Test
    void reportsOnlyBackingSlotsChangedForTheRequestedInventoryKind() {
        InventorySnapshot original = InventorySnapshot.empty();
        InventorySnapshot changed = original
                .withBackingSlot(InventoryKind.PLAYER, 4, item(Material.DIAMOND, 2))
                .withBackingSlot(
                        InventoryKind.PLAYER,
                        InventorySnapshot.HELMET_SLOT,
                        item(Material.DIAMOND_HELMET)
                )
                .withBackingSlot(InventoryKind.ENDER_CHEST, 7, item(Material.EMERALD));

        assertArrayEquals(
                new int[]{4, InventorySnapshot.HELMET_SLOT},
                original.changedBackingSlots(InventoryKind.PLAYER, changed)
        );
        assertArrayEquals(
                new int[]{7},
                original.changedBackingSlots(InventoryKind.ENDER_CHEST, changed)
        );
        assertArrayEquals(
                new int[0],
                changed.changedBackingSlots(InventoryKind.PLAYER, changed)
        );
    }
}
