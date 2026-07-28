package nl.hauntedmc.serverfeatures.features.invtools.gui;

import nl.hauntedmc.serverfeatures.features.invtools.model.InventoryKind;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventorySlotLayoutTest {

    @Test
    void playerLayoutMapsEveryStorageEquipmentAndOffhandSlotExactlyOnce() {
        Map<Integer, Integer> slots = InventorySlotLayout.mappedSlots(InventoryKind.PLAYER);

        assertEquals(41, slots.size());
        assertEquals(41, new HashSet<>(slots.values()).size());
        for (int storageSlot = 0; storageSlot < 36; storageSlot++) {
            assertTrue(slots.containsValue(storageSlot));
        }
        assertEquals(103, slots.get(0));
        assertEquals(102, slots.get(1));
        assertEquals(101, slots.get(2));
        assertEquals(100, slots.get(3));
        assertEquals(-106, slots.get(5));
        assertEquals(5, InventorySlotLayout.guiSlot(InventoryKind.PLAYER, -106).orElseThrow());
    }

    @Test
    void hotbarAppearsBelowMainStorageWithoutChangingBackingSlotNumbers() {
        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            assertEquals(
                    hotbarSlot,
                    InventorySlotLayout.backingSlot(InventoryKind.PLAYER, 36 + hotbarSlot)
                            .orElseThrow()
            );
            assertEquals(
                    36 + hotbarSlot,
                    InventorySlotLayout.guiSlot(InventoryKind.PLAYER, hotbarSlot).orElseThrow()
            );
        }
        for (int storageSlot = 9; storageSlot < 36; storageSlot++) {
            assertEquals(
                    storageSlot,
                    InventorySlotLayout.backingSlot(InventoryKind.PLAYER, storageSlot)
                            .orElseThrow()
            );
        }
    }

    @Test
    void enderLayoutMapsAllTwentySevenSlotsAndKeepsControlRowUnmapped() {
        Map<Integer, Integer> slots = InventorySlotLayout.mappedSlots(InventoryKind.ENDER_CHEST);

        assertEquals(27, slots.size());
        for (int slot = 0; slot < 27; slot++) {
            assertEquals(slot, slots.get(slot));
        }
        assertTrue(InventorySlotLayout.backingSlot(
                InventoryKind.ENDER_CHEST,
                InventorySlotLayout.ENDER_CLOSE_SLOT
        ).isEmpty());
    }

    @Test
    void sectionsDistinguishMainStorageHotbarEquipmentAndControls() {
        assertEquals(
                InventorySlotLayout.SlotSection.MAIN_STORAGE,
                InventorySlotLayout.section(InventoryKind.PLAYER, 9)
        );
        assertEquals(
                InventorySlotLayout.SlotSection.HOTBAR,
                InventorySlotLayout.section(InventoryKind.PLAYER, 36)
        );
        assertEquals(
                InventorySlotLayout.SlotSection.ARMOR,
                InventorySlotLayout.section(InventoryKind.PLAYER, 0)
        );
        assertEquals(
                InventorySlotLayout.SlotSection.OFF_HAND,
                InventorySlotLayout.section(InventoryKind.PLAYER, 5)
        );
        assertEquals(
                InventorySlotLayout.SlotSection.CONTROL,
                InventorySlotLayout.section(
                        InventoryKind.PLAYER,
                        InventorySlotLayout.PLAYER_CLOSE_SLOT
                )
        );
        assertEquals(
                InventorySlotLayout.SlotSection.ENDER_CHEST,
                InventorySlotLayout.section(InventoryKind.ENDER_CHEST, 0)
        );
    }
}
