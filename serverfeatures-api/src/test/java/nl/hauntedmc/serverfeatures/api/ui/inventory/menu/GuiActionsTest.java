package nl.hauntedmc.serverfeatures.api.ui.inventory.menu;

import nl.hauntedmc.serverfeatures.api.ui.inventory.menu.item.GuiClickContext;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuiActionsTest {

    @Test
    void renderCommandInterpolatesPlayerPlaceholder() {
        String rendered = GuiActions.renderCommand("/say hello %player%", "Remy");
        assertEquals("/say hello Remy", rendered);
    }

    @Test
    void normalizeLeadingSlashKeepsCommandExecutorFormat() {
        assertEquals("say hello", GuiActions.normalizeLeadingSlash("/say hello"));
        assertEquals("say hello", GuiActions.normalizeLeadingSlash("say hello"));
    }

    @Test
    void runPlayerCommandInterpolatesAndNormalizesCommand() {
        AtomicReference<String> executed = new AtomicReference<>();
        Player player = InterfaceProxy.of(Player.class, Map.of(
                "getName", args -> "Remy",
                "performCommand", args -> {
                    executed.set((String) args[0]);
                    return true;
                }
        ));
        GuiClickContext context = contextFor(player);

        GuiActions.runPlayerCommand("/say hi %player%").accept(context);

        assertEquals("say hi Remy", executed.get());
    }

    private static GuiClickContext contextFor(Player player) {
        TestMenu menu = new TestMenu();
        Inventory inventory = InterfaceProxy.of(Inventory.class, Map.of("getSize", args -> 9));
        InventoryView view = InterfaceProxy.of(InventoryView.class, Map.of(
                "getPlayer", args -> player,
                "getTopInventory", args -> inventory
        ));
        InventoryClickEvent event = new InventoryClickEvent(
                view,
                InventoryType.SlotType.CONTAINER,
                0,
                ClickType.LEFT,
                InventoryAction.PICKUP_ALL
        );
        return new GuiClickContext(menu, 0, event);
    }
}
