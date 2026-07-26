package nl.hauntedmc.serverfeatures.api.ui.inventory.menu.menu;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.api.ui.inventory.menu.MenuTestSupport;
import nl.hauntedmc.serverfeatures.api.ui.inventory.menu.item.GuiItem;
import nl.hauntedmc.serverfeatures.api.util.text.format.ComponentFormatter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PagedMenuTest {

    @Test
    void setPageIndexClampsWithinValidRange() {
        PagedMenu<Integer> menu = PagedMenu.<Integer>builder(MenuTestSupport.guiManager())
                .entries(List.of(1, 2, 3, 4, 5))
                .contentSlots(List.of(0, 1))
                .renderer(v -> GuiItem.builder().factory(p -> null).build())
                .build();

        menu.setPageIndex(-10);
        assertEquals(0, menu.pageIndex());

        menu.setPageIndex(99);
        assertEquals(2, menu.pageIndex());
    }

    @Test
    void titleIncludesCurrentPageWhenEnabled() {
        PagedMenu<Integer> menu = PagedMenu.<Integer>builder(MenuTestSupport.guiManager())
                .title(Component.text("Items"))
                .entries(List.of(1, 2, 3, 4, 5))
                .contentSlots(List.of(0, 1))
                .renderer(v -> GuiItem.builder().factory(p -> null).build())
                .build();
        menu.setPageIndex(2);

        String title = ComponentFormatter.serialize(menu.titleFor(null))
                .format(ComponentFormatter.Serializer.Format.PLAIN)
                .build();

        assertTrue(title.contains("(3/3)"));
    }

    @Test
    void builderRejectsNavigationSlotsInsideContentSlots() {
        assertThrows(IllegalArgumentException.class, () -> PagedMenu.<Integer>builder(MenuTestSupport.guiManager())
                .contentSlots(List.of(45, 1, 2))
                .entries(List.of(1))
                .renderer(v -> GuiItem.builder().factory(p -> null).build())
                .build());
    }
}
