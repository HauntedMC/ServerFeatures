package nl.hauntedmc.serverfeatures.common.gui;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central GUI coordinator:
 * - Registers listeners
 * - Tracks open menus and per-player back stacks
 * - Opens, reopens (title-safe), navigates back
 * - Cleans up on quit/kick
 * - Prevents reopen loops on non-user closes
 * Anti-duplication hardening:
 * - Cancel ALL clicks when our GUI is the top inventory (already in place)
 * - Cancel drags targeting our top inventory (already in place)
 * - Cancel creative actions and drop events when a GUI is open
 * - Debounce clicks per-player to avoid double-execution spam
 */
public final class GuiManager implements Listener {
    private static GuiManager INSTANCE;

    private final Map<UUID, Deque<GuiMenu>> backStacks = new ConcurrentHashMap<>();
    private final Map<UUID, GuiMenu> openMenus = new ConcurrentHashMap<>();
    private final Map<UUID, Long> clickDebounce = new ConcurrentHashMap<>(); // ms timestamp
    private volatile BukkitBaseFeature<?> feature;

    // Debounce window in milliseconds
    private static final long CLICK_DEBOUNCE_MS = 120L;

    private GuiManager() {}

    public static GuiManager get() {
        if (INSTANCE == null) INSTANCE = new GuiManager();
        return INSTANCE;
    }

    public void init(BukkitBaseFeature<?> feature) {
        this.feature = feature;
        feature.getLifecycleManager().getListenerManager().registerListener(this);
    }

    public void shutdown() {
        openMenus.clear();
        backStacks.clear();
        clickDebounce.clear();
        this.feature = null;
    }

    public BukkitBaseFeature<?> feature() { return feature; }

    public Plugin plugin() { return feature.getPlugin(); }

    /** Open a root menu; clears back stack. */
    public void openRoot(Player p, GuiMenu menu) {
        ensureReady();
        if (!isUsable(p)) return;
        backStacks.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>()).clear();
        openInternal(p, menu, false, OpenSemantics.REPLACE);
    }

    /** Open a child menu; pushes current menu onto back stack. */
    public void openChild(Player p, GuiMenu menu) {
        ensureReady();
        if (!isUsable(p)) return;
        GuiMenu current = openMenus.get(p.getUniqueId());
        if (current != null) {
            backStacks.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>()).push(current);
        }
        openInternal(p, menu, true, OpenSemantics.REPLACE);
    }

    /** Reopen the SAME menu without pushing to the back stack (used by paging/refresh). */
    public void reopenSame(Player p, GuiMenu sameMenu) {
        ensureReady();
        if (!isUsable(p)) return;
        // Only allow if same menu is indeed the open one
        GuiMenu current = openMenus.get(p.getUniqueId());
        if (current != sameMenu) return;
        openInternal(p, sameMenu, false, OpenSemantics.REOPEN);
    }

    /** Navigate back; returns false if no previous menu. */
    public boolean goBack(Player p) {
        Deque<GuiMenu> stack = backStacks.get(p.getUniqueId());
        if (stack == null || stack.isEmpty()) return false;
        GuiMenu previous = stack.pop();
        openInternal(p, previous, false, OpenSemantics.REPLACE);
        return true;
    }

    public boolean isMenuOpen(Player p) {
        return openMenus.containsKey(p.getUniqueId());
    }

    private enum OpenSemantics { REPLACE, REOPEN }

    private void ensureReady() {
        if (feature == null) throw new IllegalStateException("GuiManager not initialized");
    }

    private boolean isUsable(Player p) {
        return p != null && p.isOnline() && !p.isDead();
    }

    private void openInternal(Player p, GuiMenu menu, boolean child, OpenSemantics sem) {
        feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> doOpen(p, menu, child, sem));
    }

    private void doOpen(Player p, GuiMenu menu, boolean child, OpenSemantics sem) {
        if (!isUsable(p)) return;

        // Prepare menu state before we compute title or create inventory
        menu.prepare(child);

        Component newTitle = menu.titleFor(p);
        Inventory previousTop = null;
        GuiMenu currentlyOpen = openMenus.get(p.getUniqueId());
        if (currentlyOpen == menu) {
            p.getOpenInventory();
            previousTop = p.getOpenInventory().getTopInventory();
        }

        // Always (re)create inventory because titles cannot be changed in-place
        Inventory inv = menu.createInventory(newTitle);
        menu.populate(p, inv);

        if (sem == OpenSemantics.REOPEN && previousTop != null) {
            // Avoid extra client animations by reopening next tick
            feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(() -> {
                if (!isUsable(p)) return;
                p.openInventory(inv);
                openMenus.put(p.getUniqueId(), menu);
                menu.onOpen(p);
            });
        } else {
            p.openInventory(inv);
            openMenus.put(p.getUniqueId(), menu);
            menu.onOpen(p);
        }
    }

    // --------- Listeners ---------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        GuiMenu menu = openMenus.get(p.getUniqueId());
        if (menu == null) return;

        // Only handle clicks where the TOP inventory is our menu; cancel broadly to prevent item movement.
        if (!menu.owns(e.getView().getTopInventory())) return;

        // Anti-spam debounce: ignore if clicks fire too fast (prevents double-exec)
        long now = System.currentTimeMillis();
        Long prev = clickDebounce.put(p.getUniqueId(), now);
        if (prev != null && (now - prev) < CLICK_DEBOUNCE_MS) {
            e.setCancelled(true);
            return;
        }

        // GUIs are not for item moving by default — cancel everything
        e.setCancelled(true);

        // Only dispatch clicks occurring inside the menu's top inventory
        if (e.getClickedInventory() == null) return;
        if (!menu.owns(e.getClickedInventory())) return;

        int slot = e.getSlot();
        try {
            menu.handleClick(p, slot, e);
        } catch (Throwable t) {
            feature.getLogger().severe("Error handling GUI click: " + t.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        GuiMenu menu = openMenus.get(p.getUniqueId());
        if (menu == null) return;

        // Drag events can span both inventories. Cancel if ANY raw slot targets our top inventory.
        Inventory top = e.getView().getTopInventory();
        if (!menu.owns(top)) return;

        for (int rawSlot : e.getRawSlots()) {
            // In an InventoryView, raw slots [0..top.size-1] map to top inventory.
            if (rawSlot < top.getSize()) {
                e.setCancelled(true);
                return;
            }
        }
    }

    // Cancel creative inventory interactions when our GUI is open (extra hardening)
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreative(InventoryCreativeEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        GuiMenu menu = openMenus.get(p.getUniqueId());
        if (menu == null) return;
        if (menu.owns(e.getView().getTopInventory())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (isMenuOpen(p)) e.setCancelled(true);
    }


    // Prevent dropping items (Q) while GUI open — avoids edge-case dupes
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        GuiMenu menu = openMenus.get(p.getUniqueId());
        if (menu == null) return;
        // If any GUI is open for this player, disallow dropping to avoid state desyncs
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        GuiMenu menu = openMenus.get(p.getUniqueId());
        if (menu == null) return;
        if (!menu.owns(e.getInventory())) return;

        try {
            menu.onClose(p, e.getReason());
        } catch (Throwable t) {
            feature.getLogger().severe("Error in onClose: " + t.getMessage());
        }

        boolean reopen = menu.shouldReopen() && menu.allowReopenFor(e.getReason());
        if (!reopen) {
            openMenus.remove(p.getUniqueId());
        } else {
            // reset flag and reopen
            menu.clearReopenRequest();
            reopenSame(p, menu);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        cleanup(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (isMenuOpen(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent e) {
        cleanup(e.getPlayer().getUniqueId());
    }

    private void cleanup(UUID uuid) {
        openMenus.remove(uuid);
        backStacks.remove(uuid);
        clickDebounce.remove(uuid);
    }
}
