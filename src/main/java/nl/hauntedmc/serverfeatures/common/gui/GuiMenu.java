package nl.hauntedmc.serverfeatures.common.gui;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.common.gui.item.GuiClickContext;
import nl.hauntedmc.serverfeatures.common.gui.item.GuiItem;
import nl.hauntedmc.serverfeatures.common.gui.item.GuiItems;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Abstract GUI base. Supports:
 * - Title rendering
 * - Filler + fixed items
 * - Back button (handled here)
 * - Reopen requests (auto re-open on close, with reason filter)
 * - Lifecycle hooks: prepare, onOpen, onClose(reason)
 * - Optional sounds on open/close/back
 * Builders in concrete classes call the static validation helpers to prevent slot collisions.
 * Notes:
 * - Actions for items are defined via GuiItem.onClick(...) or builder helpers (run commands).
 * - Use GuiItem#closeOnClick(true) to close the menu after pressing an item.
 */
public abstract class GuiMenu implements InventoryHolder {
    protected final Component baseTitle;
    protected final int size; // 9..54, multiple of 9
    protected final boolean showPageInTitle;
    protected final ItemStack filler;
    protected final Map<Integer, GuiItem> fixedItems = new HashMap<>();

    protected Inventory inventory;

    // Navigation
    protected boolean addBackButton;
    protected int backSlot;

    // Reopen control
    private boolean requestReopen = false;

    // Optional UX
    protected Sound openSound = null;
    protected float openVol = 1f, openPitch = 1f;
    protected Sound backSound = null;
    protected float backVol = 1f, backPitch = 1f;
    protected Sound closeSound = null;
    protected float closeVol = 1f, closePitch = 1f;

    protected GuiMenu(
            Component baseTitle,
            int size,
            boolean showPageInTitle,
            ItemStack filler,
            Map<Integer, GuiItem> items,
            boolean addBackButton,
            int backSlot
    ) {
        if (size <= 0 || size % 9 != 0 || size > 54) throw new IllegalArgumentException("Invalid inventory size: " + size);
        this.baseTitle = Objects.requireNonNull(baseTitle, "baseTitle");
        this.size = size;
        this.showPageInTitle = showPageInTitle;
        this.filler = filler;
        if (items != null) this.fixedItems.putAll(items);
        this.addBackButton = addBackButton;
        this.backSlot = backSlot;
    }

    /** Internal hook called before (re)rendering; child indicates opened as nested. */
    public void prepare(boolean child) {
        // If nested and no explicit back slot, default to last slot
        if (child && addBackButton && backSlot < 0) backSlot = size - 1;
    }

    /** Called by manager after actual opening. */
    public void onOpen(Player p) {
        if (openSound != null) p.playSound(p.getLocation(), openSound, openVol, openPitch);
    }

    /** Called by manager before reference is removed on close. */
    public void onClose(Player p, InventoryCloseEvent.Reason reason) {
        if (closeSound != null) p.playSound(p.getLocation(), closeSound, closeVol, closePitch);
    }

    /** Back button click UX hook. */
    protected void onBack(Player p) {
        if (backSound != null) p.playSound(p.getLocation(), backSound, backVol, backPitch);
    }

    /** If true, the manager will reopen this same menu next tick after close. */
    public boolean shouldReopen() { return requestReopen; }

    /** Request the manager to reopen this same menu on close. */
    public void requestReopen() { this.requestReopen = true; }

    /** Manager will call this after honoring the request. */
    public void clearReopenRequest() { this.requestReopen = false; }

    /** Filter: by default don't reopen on disconnect/kick/teleport/PLUGIN. */
    public boolean allowReopenFor(InventoryCloseEvent.Reason reason) {
        return switch (reason) {
            case UNLOADED, DISCONNECT, OPEN_NEW, TELEPORT, DEATH, PLUGIN -> false;
            default -> true;
        };
    }

    /** Exposed so manager can compute title BEFORE creating inventory. */
    public Component titleFor(Player p) { return baseTitle; }

    /** Create new inventory with the given title (Paper supports Component). */
    public Inventory createInventory(Component title) {
        this.inventory = Bukkit.createInventory(this, size, title);
        return this.inventory;
    }

    /** Populate the inventory: filler, fixed items, back button, and allow subclass additions. */
    public void populate(Player p, Inventory inv) {
        if (filler != null) {
            for (int i = 0; i < size; i++) inv.setItem(i, filler);
        }
        placeFixedItems(p, inv);
        if (addBackButton) placeBackButton(inv);
        afterPopulate(p, inv);
    }

    /** Subclass extension point after population. */
    protected void afterPopulate(Player p, Inventory inv) {}

    protected void placeFixedItems(Player p, Inventory inv) {
        for (Map.Entry<Integer, GuiItem> e : fixedItems.entrySet()) {
            int slot = e.getKey();
            if (slot < 0 || slot >= size) continue;
            GuiItem gi = e.getValue();
            if (!gi.visibleTo(p)) {
                var repl = gi.replacementOrNull(p);
                if (repl != null) inv.setItem(slot, repl);
                continue;
            }
            inv.setItem(slot, gi.renderFor(p));
        }
    }

    protected void placeBackButton(Inventory inv) {
        if (backSlot < 0 || backSlot >= size) return;
        inv.setItem(backSlot, GuiItems.button(org.bukkit.Material.ARROW, Component.text("Back")));
    }

    /** Dispatch click to back button or fixed item; subclass may override. */
    public void handleClick(Player p, int slot, org.bukkit.event.inventory.InventoryClickEvent e) {
        if (addBackButton && slot == backSlot) {
            onBack(p);
            GuiManager.get().goBack(p);
            return;
        }
        GuiItem gi = fixedItems.get(slot);
        if (gi == null) return;
        if (!gi.visibleTo(p)) return; // ignore clicks on hidden items
        gi.click(p, new GuiClickContext(this, slot, e));
    }

    @Override public @NotNull Inventory getInventory() { return inventory; }

    /** Ownership is by InventoryHolder identity. */
    public boolean owns(Inventory inv) { return inv != null && inv.getHolder() == this; }

    /* ---------- Shared validation helpers for builders ---------- */

    protected static void validateFixedItems(Map<Integer, GuiItem> items, int size, boolean backEnabled, int backSlot, String who) {
        for (int s : items.keySet()) {
            if (s < 0 || s >= size)
                throw new IllegalArgumentException(who + ": fixed item slot out of bounds: " + s);
        }
        if (backEnabled && backSlot >= 0) {
            if (backSlot >= size) throw new IllegalArgumentException(who + ": backSlot out of bounds");
            if (items.containsKey(backSlot))
                throw new IllegalArgumentException(who + ": fixed items cannot override backSlot " + backSlot);
        }
    }

    protected static void ensureSlotsInBounds(Iterable<Integer> slots, int size, String label) {
        for (int s : slots) {
            if (s < 0 || s >= size) throw new IllegalArgumentException(label + " contains out-of-bounds slot: " + s);
        }
    }

    protected static void validateNoCollisionsWithBackAndFixed(
            Map<Integer, GuiItem> fixed,
            int size,
            boolean backEnabled,
            int backSlot,
            Set<Integer> reserved,
            String who
    ) {
        ensureSlotsInBounds(reserved, size, who + ".reservedSlots");
        for (int s : fixed.keySet()) {
            if (reserved.contains(s)) throw new IllegalArgumentException(who + ": fixed item collides with reserved slot " + s);
        }
        if (backEnabled && backSlot >= 0) {
            if (reserved.contains(backSlot)) throw new IllegalArgumentException(who + ": back button collides with reserved slot " + backSlot);
        }
    }

    /* ---------- Optional UX setters ---------- */

    public GuiMenu openSound(Sound s, float vol, float pitch) { this.openSound = s; this.openVol = vol; this.openPitch = pitch; return this; }
    public GuiMenu backSound(Sound s, float vol, float pitch) { this.backSound = s; this.backVol = vol; this.backPitch = pitch; return this; }
    public GuiMenu closeSound(Sound s, float vol, float pitch) { this.closeSound = s; this.closeVol = vol; this.closePitch = pitch; return this; }
}
