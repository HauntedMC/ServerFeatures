package nl.hauntedmc.serverfeatures.api.ui.inventory.menu.menu;

import nl.hauntedmc.serverfeatures.api.ui.inventory.menu.MenuTestSupport;
import nl.hauntedmc.serverfeatures.util.InterfaceProxy;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfirmationMenuTest {

    @Test
    void builderRejectsOverlappingActionSlots() {
        assertThrows(IllegalArgumentException.class, () -> ConfirmationMenu.builder(MenuTestSupport.guiManager())
                .yesSlot(11)
                .noSlot(11)
                .build());
    }

    @Test
    void builderRejectsOutOfBoundsActionSlots() {
        assertThrows(IllegalArgumentException.class, () -> ConfirmationMenu.builder(MenuTestSupport.guiManager())
                .size(9)
                .yesSlot(9)
                .build());
    }

    @Test
    void yesClickRunsConfirmActionAndClosesInventory() {
        AtomicInteger confirmCalls = new AtomicInteger();
        AtomicInteger closeCalls = new AtomicInteger();
        Player player = InterfaceProxy.of(Player.class, Map.of(
                "closeInventory", args -> {
                    closeCalls.incrementAndGet();
                    return null;
                }
        ));

        ConfirmationMenu menu = ConfirmationMenu.builder(MenuTestSupport.guiManager())
                .filler(null)
                .yesSlot(2)
                .noSlot(6)
                .onConfirm(confirmCalls::incrementAndGet)
                .build();

        menu.handleClick(player, 2, null);

        assertEquals(1, confirmCalls.get());
        assertEquals(1, closeCalls.get());
    }
}
