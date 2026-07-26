package nl.hauntedmc.serverfeatures.api.ui.inventory.menu.item;

import nl.hauntedmc.serverfeatures.api.ui.inventory.menu.TestMenu;
import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiClickContextTest {

    @Test
    void exposesMenuSlotPlayerAndRawEvent() {
        TestMenu menu = new TestMenu();
        Player player = InterfaceProxy.of(Player.class, Map.of());
        InventoryClickEvent event = clickEvent(player, ClickType.LEFT, 4);

        GuiClickContext context = new GuiClickContext(menu, 4, event);

        assertSame(menu, context.menu());
        assertEquals(4, context.slot());
        assertSame(event, context.rawEvent());
        assertSame(player, context.player());
    }

    @Test
    void clickTypeHelpersMapToExpectedFlags() {
        TestMenu menu = new TestMenu();
        Player player = InterfaceProxy.of(Player.class, Map.of());

        GuiClickContext left = new GuiClickContext(menu, 0, clickEvent(player, ClickType.SHIFT_LEFT, 0));
        GuiClickContext right = new GuiClickContext(menu, 0, clickEvent(player, ClickType.RIGHT, 0));
        GuiClickContext number = new GuiClickContext(menu, 0, clickEvent(player, ClickType.NUMBER_KEY, 0));
        GuiClickContext middle = new GuiClickContext(menu, 0, clickEvent(player, ClickType.MIDDLE, 0));

        assertTrue(left.isLeftClick());
        assertTrue(left.isShiftClick());
        assertFalse(left.isRightClick());

        assertTrue(right.isRightClick());
        assertFalse(right.isShiftClick());
        assertFalse(right.isLeftClick());

        assertTrue(number.isNumberKey());
        assertTrue(middle.isMiddleClick());
    }

    private static InventoryClickEvent clickEvent(Player player, ClickType clickType, int slot) {
        Inventory inventory = InterfaceProxy.of(Inventory.class, Map.of("getSize", args -> 9));
        InventoryView view = InterfaceProxy.of(InventoryView.class, Map.of(
                "getPlayer", args -> player,
                "getTopInventory", args -> inventory
        ));
        return new InventoryClickEvent(
                view,
                InventoryType.SlotType.CONTAINER,
                slot,
                clickType,
                InventoryAction.PICKUP_ALL
        );
    }
}
