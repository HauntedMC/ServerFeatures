package nl.hauntedmc.serverfeatures.common.gui.menu;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.common.gui.GuiMenu;
import nl.hauntedmc.serverfeatures.common.gui.item.GuiItem;
import nl.hauntedmc.serverfeatures.lifecycle.FeatureGUIManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Simple single-page menu with a fluent builder.
 */
public final class SimpleMenu extends GuiMenu {

    private SimpleMenu(
            FeatureGUIManager gui,
            Component baseTitle,
            int size,
            boolean showPageInTitle,
            ItemStack filler,
            Map<Integer, GuiItem> items,
            boolean addBackButton,
            int backSlot
    ) {
        super(gui, baseTitle, size, showPageInTitle, filler, items, addBackButton, backSlot);
    }

    public static Builder builder(FeatureGUIManager gui) { return new Builder(gui); }

    @Override protected void afterPopulate(Player p, Inventory inv) { /* no-op */ }

    public static final class Builder {
        private final FeatureGUIManager gui;
        private Component title = Component.text("Menu");
        private int size = 9 * 3;
        private boolean pageInTitle = false;
        private ItemStack filler = null;
        private final Map<Integer, GuiItem> items = new HashMap<>();
        private boolean backButton = false;
        private int backSlot = -1;

        public Builder(FeatureGUIManager gui) {
            this.gui = Objects.requireNonNull(gui, "gui");
        }

        public Builder title(Component t) { this.title = t; return this; }
        public Builder size(int s) { this.size = s; return this; }
        public Builder showPageInTitle(boolean b) { this.pageInTitle = b; return this; }
        public Builder filler(ItemStack i) { this.filler = i; return this; }
        public Builder item(int slot, GuiItem item) { this.items.put(slot, item); return this; }
        public Builder backButton(boolean enabled) { this.backButton = enabled; return this; }
        public Builder backButtonSlot(int slot) { this.backSlot = slot; return this; }

        public SimpleMenu build() {
            if (size <= 0 || size % 9 != 0 || size > 54) throw new IllegalArgumentException("Invalid size");
            validateFixedItems(items, size, backButton, backSlot, "SimpleMenu");
            return new SimpleMenu(gui, title, size, pageInTitle, filler, items, backButton, backSlot);
        }
    }
}
