package nl.hauntedmc.serverfeatures.api.ui.inventory.menu;

import nl.hauntedmc.serverfeatures.api.ui.inventory.menu.item.GuiItem;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiMenuTest {

    @Test
    void validateFixedItemsRejectsOutOfBoundsAndBackCollisions() {
        Map<Integer, GuiItem> items = new HashMap<>();
        items.put(9, null);

        assertThrows(IllegalArgumentException.class,
                () -> GuiMenu.validateFixedItems(items, 9, false, -1, "GuiMenu"));

        Map<Integer, GuiItem> backCollision = new HashMap<>();
        backCollision.put(8, null);
        assertThrows(IllegalArgumentException.class,
                () -> GuiMenu.validateFixedItems(backCollision, 9, true, 8, "GuiMenu"));
    }

    @Test
    void ensureSlotsInBoundsRejectsInvalidSlot() {
        assertThrows(IllegalArgumentException.class,
                () -> GuiMenu.ensureSlotsInBounds(Set.of(0, 1, 9), 9, "reserved"));
    }

    @Test
    void validateNoCollisionsWithBackAndFixedRejectsReservedCollisions() {
        Map<Integer, GuiItem> fixed = new HashMap<>();
        fixed.put(3, null);

        assertThrows(IllegalArgumentException.class,
                () -> GuiMenu.validateNoCollisionsWithBackAndFixed(fixed, 9, false, -1, Set.of(3), "GuiMenu"));

        assertThrows(IllegalArgumentException.class,
                () -> GuiMenu.validateNoCollisionsWithBackAndFixed(Map.of(), 9, true, 7, Set.of(7), "GuiMenu"));
    }

    @Test
    void allowReopenForDisallowsTerminalCloseReasons() {
        TestMenu menu = new TestMenu();

        assertFalse(menu.allowReopenFor(InventoryCloseEvent.Reason.DISCONNECT));
        assertFalse(menu.allowReopenFor(InventoryCloseEvent.Reason.PLUGIN));
        assertTrue(menu.allowReopenFor(InventoryCloseEvent.Reason.CANT_USE));
    }
}
