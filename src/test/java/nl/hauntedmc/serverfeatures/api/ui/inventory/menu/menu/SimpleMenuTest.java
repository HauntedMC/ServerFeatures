package nl.hauntedmc.serverfeatures.api.ui.inventory.menu.menu;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.api.ui.inventory.menu.MenuTestSupport;
import nl.hauntedmc.serverfeatures.api.ui.inventory.menu.item.GuiItem;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureGUIManager;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleMenuTest {

    @Test
    void builderRejectsInvalidInventorySize() {
        assertThrows(IllegalArgumentException.class, () -> SimpleMenu.builder(MenuTestSupport.guiManager())
                .size(10)
                .build());
    }

    @Test
    void builderRejectsBackSlotCollisionWithFixedItem() {
        assertThrows(IllegalArgumentException.class, () -> SimpleMenu.builder(MenuTestSupport.guiManager())
                .size(9)
                .backButton(true)
                .backButtonSlot(8)
                .item(8, GuiItem.builder().factory(p -> null).build())
                .build());
    }

    @Test
    void builderCreatesMenuWithExpectedCoreBehavior() {
        FeatureGUIManager gui = MenuTestSupport.guiManager();
        SimpleMenu menu = SimpleMenu.builder(gui)
                .title(Component.text("Simple"))
                .size(27)
                .item(5, GuiItem.builder().factory(p -> null).build())
                .build();

        assertSame(gui, menu.guiManager());
        assertEquals(Component.text("Simple"), menu.titleFor(null));
        assertFalse(menu.shouldReopen());
        menu.requestReopen();
        assertTrue(menu.shouldReopen());
        menu.clearReopenRequest();
        assertFalse(menu.shouldReopen());
        assertTrue(menu.allowReopenFor(InventoryCloseEvent.Reason.PLAYER));
        assertFalse(menu.allowReopenFor(InventoryCloseEvent.Reason.DISCONNECT));
    }
}
