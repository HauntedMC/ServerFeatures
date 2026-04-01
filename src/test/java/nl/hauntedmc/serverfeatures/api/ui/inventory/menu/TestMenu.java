package nl.hauntedmc.serverfeatures.api.ui.inventory.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Map;

public final class TestMenu extends GuiMenu {

    public TestMenu() {
        super(MenuTestSupport.guiManager(), Component.text("Test"), 9, false, null, Map.of(), false, -1);
    }

    @Override
    protected void afterPopulate(Player p, Inventory inv) {
        // no-op
    }
}
