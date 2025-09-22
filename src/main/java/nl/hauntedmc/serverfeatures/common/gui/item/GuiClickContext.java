package nl.hauntedmc.serverfeatures.common.gui.item;

import nl.hauntedmc.serverfeatures.common.gui.GuiManager;
import nl.hauntedmc.serverfeatures.common.gui.GuiMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Context for a GUI click, passed into GuiItem actions.
 * Provides:
 * - The originating menu
 * - The clicked slot
 * - Raw InventoryClickEvent
 * - Helpers for common navigation and click type checks
 */
public final class GuiClickContext {
    private final GuiMenu menu;
    private final int slot;
    private final InventoryClickEvent event;

    public GuiClickContext(GuiMenu menu, int slot, InventoryClickEvent event) {
        this.menu = menu;
        this.slot = slot;
        this.event = event;
    }

    public GuiMenu menu() { return menu; }
    public int slot() { return slot; }
    public InventoryClickEvent rawEvent() { return event; }
    public Player player() { return (Player) event.getWhoClicked(); }

    public boolean isLeftClick() { return event.getClick() == ClickType.LEFT || event.getClick() == ClickType.SHIFT_LEFT; }
    public boolean isRightClick() { return event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT; }
    public boolean isShiftClick() { return event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT; }
    public boolean isNumberKey() { return event.getClick() == ClickType.NUMBER_KEY; }
    public boolean isMiddleClick() { return event.getClick() == ClickType.MIDDLE; }

    public void openChild(GuiMenu child) { GuiManager.get().openChild(player(), child); }
    public void openRoot(GuiMenu root) { GuiManager.get().openRoot(player(), root); }
    public void goBack() { GuiManager.get().goBack(player()); }
    public void reopenSame() { GuiManager.get().reopenSame(player(), menu); }
}
