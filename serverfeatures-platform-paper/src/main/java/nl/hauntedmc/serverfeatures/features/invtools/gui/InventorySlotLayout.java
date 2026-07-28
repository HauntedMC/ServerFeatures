package nl.hauntedmc.serverfeatures.features.invtools.gui;

import nl.hauntedmc.serverfeatures.features.invtools.model.InventoryKind;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventorySnapshot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Stable translation between the staff GUI and Minecraft's persisted player slots.
 */
public final class InventorySlotLayout {

    public static final int PLAYER_GUI_SIZE = 54;
    public static final int ENDER_GUI_SIZE = 36;
    public static final int PLAYER_CLOSE_SLOT = 49;
    public static final int ENDER_CLOSE_SLOT = 31;

    private static final Map<Integer, Integer> PLAYER_SLOTS = createPlayerSlots();
    private static final Map<Integer, Integer> ENDER_SLOTS = createEnderSlots();

    private InventorySlotLayout() {
    }

    public static int guiSize(InventoryKind kind) {
        return kind == InventoryKind.PLAYER ? PLAYER_GUI_SIZE : ENDER_GUI_SIZE;
    }

    public static int closeSlot(InventoryKind kind) {
        return kind == InventoryKind.PLAYER ? PLAYER_CLOSE_SLOT : ENDER_CLOSE_SLOT;
    }

    public static Map<Integer, Integer> mappedSlots(InventoryKind kind) {
        return kind == InventoryKind.PLAYER ? PLAYER_SLOTS : ENDER_SLOTS;
    }

    public static OptionalInt backingSlot(InventoryKind kind, int guiSlot) {
        Integer slot = mappedSlots(kind).get(guiSlot);
        return slot == null ? OptionalInt.empty() : OptionalInt.of(slot);
    }

    public static OptionalInt guiSlot(InventoryKind kind, int backingSlot) {
        return mappedSlots(kind).entrySet().stream()
                .filter(entry -> entry.getValue() == backingSlot)
                .mapToInt(Map.Entry::getKey)
                .findFirst();
    }

    public static SlotSection section(InventoryKind kind, int guiSlot) {
        OptionalInt backingSlot = backingSlot(kind, guiSlot);
        if (backingSlot.isEmpty()) {
            return SlotSection.CONTROL;
        }
        if (kind == InventoryKind.ENDER_CHEST) {
            return SlotSection.ENDER_CHEST;
        }
        int slot = backingSlot.getAsInt();
        if (slot >= 0 && slot < 9) {
            return SlotSection.HOTBAR;
        }
        if (slot >= 9 && slot < InventorySnapshot.STORAGE_SIZE) {
            return SlotSection.MAIN_STORAGE;
        }
        return slot == InventorySnapshot.OFF_HAND_SLOT
                ? SlotSection.OFF_HAND
                : SlotSection.ARMOR;
    }

    private static Map<Integer, Integer> createPlayerSlots() {
        Map<Integer, Integer> slots = new LinkedHashMap<>();
        slots.put(0, InventorySnapshot.HELMET_SLOT);
        slots.put(1, InventorySnapshot.CHESTPLATE_SLOT);
        slots.put(2, InventorySnapshot.LEGGINGS_SLOT);
        slots.put(3, InventorySnapshot.BOOTS_SLOT);
        slots.put(5, InventorySnapshot.OFF_HAND_SLOT);
        for (int backingSlot = 9; backingSlot < 36; backingSlot++) {
            slots.put(backingSlot, backingSlot);
        }
        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            slots.put(36 + hotbarSlot, hotbarSlot);
        }
        return Collections.unmodifiableMap(slots);
    }

    private static Map<Integer, Integer> createEnderSlots() {
        Map<Integer, Integer> slots = new LinkedHashMap<>();
        for (int slot = 0; slot < 27; slot++) {
            slots.put(slot, slot);
        }
        return Collections.unmodifiableMap(slots);
    }

    public enum SlotSection {
        MAIN_STORAGE,
        HOTBAR,
        ARMOR,
        OFF_HAND,
        ENDER_CHEST,
        CONTROL
    }
}
