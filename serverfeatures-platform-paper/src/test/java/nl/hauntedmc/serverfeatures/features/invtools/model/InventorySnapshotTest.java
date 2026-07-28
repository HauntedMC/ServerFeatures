package nl.hauntedmc.serverfeatures.features.invtools.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static nl.hauntedmc.serverfeatures.features.invtools.support.TestItemStacks.item;

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
                .withBackingSlot(InventoryKind.PLAYER, InventorySnapshot.HELMET_SLOT,
                        item(Material.STONE, 64))
                .withBackingSlot(InventoryKind.PLAYER, InventorySnapshot.CHESTPLATE_SLOT,
                        item(Material.STONE, 64))
                .withBackingSlot(InventoryKind.PLAYER, InventorySnapshot.LEGGINGS_SLOT,
                        item(Material.STONE, 64))
                .withBackingSlot(InventoryKind.PLAYER, InventorySnapshot.BOOTS_SLOT,
                        item(Material.STONE, 64))
                .withBackingSlot(InventoryKind.PLAYER, InventorySnapshot.OFF_HAND_SLOT,
                        item(Material.STONE, 64));

        InventorySnapshot.InsertionResult result = full.insert(
                InventoryKind.PLAYER,
                item(Material.DIAMOND)
        );

        assertTrue(result.remainder().isSimilar(item(Material.DIAMOND)));
        assertEquals(1, result.remainder().getAmount());
    }

    @Test
    void reportsOnlyBackingSlotsChangedForTheRequestedInventoryKind() {
        InventorySnapshot original = InventorySnapshot.empty();
        InventorySnapshot changed = original
                .withBackingSlot(InventoryKind.PLAYER, 4, item(Material.DIAMOND, 2))
                .withBackingSlot(InventoryKind.PLAYER, InventorySnapshot.HELMET_SLOT,
                        item(Material.DIAMOND_HELMET))
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
