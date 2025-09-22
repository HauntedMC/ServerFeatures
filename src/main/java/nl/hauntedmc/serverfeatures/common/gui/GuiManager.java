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
 * Central coordinator for GUI menus.
 * Responsibilities:
 * - Registers event listeners
 * - Tracks open menus per player and a back-stack of previous menus
 * - Opens root/child menus and supports reopen of the same menu
 * - Navigates back in history
 * - Cleans up on quit/kick
 * Anti-duplication hardening:
 * - Cancels all clicks when our GUI is open
 * - Cancels drags that target the top inventory
 * - Cancels creative interactions when our GUI is open
 * - Cancels item drops and pickups while a GUI is open
 * - Debounces click handling per player to avoid double execution
 * Threading:
 * - All inventory operations happen on the server main thread via TaskManager scheduling.
 */
public final class GuiManager implements Listener {
    private static GuiManager INSTANCE;

    private final Map<UUID, Deque<GuiMenu>> backStacks = new ConcurrentHashMap<>();
    private final Map<UUID, GuiMenu> openMenus = new ConcurrentHashMap<>();
    private final Map<UUID, Long> clickDebounce = new ConcurrentHashMap<>();
    private volatile BukkitBaseFeature<?> feature;

    private static final long CLICK_DEBOUNCE_MS = 120L;

    private GuiManager() {}

    public static GuiManager get() {
        if (INSTANCE == null) INSTANCE = new GuiManager();
        return INSTANCE;
    }

    /**
     * Initialize and register event listeners.
     * Must be called once during feature initialization.
     */
    public void init(BukkitBaseFeature<?> feature) {
        this.feature = feature;
        feature.getLifecycleManager().getListenerManager().registerListener(this);
    }

    /**
     * Clear all in-memory state. Intended for plugin disable.
     * Note: does not forcibly close player inventories; call closeAllAndClear() if desired.
     */
    public void shutdown() {
        openMenus.clear();
        backStacks.clear();
        clickDebounce.clear();
        this.feature = null;
    }

    /** Feature reference that owns this manager. */
    public BukkitBaseFeature<?> feature() { return feature; }

    /** Owning plugin instance, useful for scheduling or logging. */
    public Plugin plugin() { return feature.getPlugin(); }

    /**
     * Open a new root menu, clearing the back stack.
     * Any existing open menu is replaced.
     */
    public void openRoot(Player p, GuiMenu menu) {
        ensureReady();
        if (!isUsable(p)) return;
        backStacks.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>()).clear();
        openInternal(p, menu, false, OpenSemantics.REPLACE);
    }

    /**
     * Open a child menu, pushing the current menu onto the back stack if present.
     */
    public void openChild(Player p, GuiMenu menu) {
        ensureReady();
        if (!isUsable(p)) return;
        GuiMenu current = openMenus.get(p.getUniqueId());
        if (current != null) {
            backStacks.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>()).push(current);
        }
        openInternal(p, menu, true, OpenSemantics.REPLACE);
    }

    /**
     * Reopen the same menu instance in place, without affecting the back stack.
     * Useful for pagination or refreshes that change title or content.
     */
    public void reopenSame(Player p, GuiMenu sameMenu) {
        ensureReady();
        if (!isUsable(p)) return;
        GuiMenu current = openMenus.get(p.getUniqueId());
        if (current != sameMenu) return;
        openInternal(p, sameMenu, false, OpenSemantics.REOPEN);
    }

    /**
     * Navigate back to the previous menu for this player.
     * Returns false if the back stack is empty.
     */
    public boolean goBack(Player p) {
        Deque<GuiMenu> stack = backStacks.get(p.getUniqueId());
        if (stack == null || stack.isEmpty()) return false;
        GuiMenu previous = stack.pop();
        openInternal(p, previous, false, OpenSemantics.REPLACE);
        return true;
    }

    /** Whether any tracked menu is currently open for this player. */
    public boolean isMenuOpen(Player p) {
        return openMenus.containsKey(p.getUniqueId());
    }

    /** Close all tracked GUIs and clear stacks. Use on disable if you need to force-close. */
    public void closeAllAndClear() {
        for (UUID uuid : openMenus.keySet()) {
            Player p = plugin().getServer().getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
            }
        }
        shutdown();
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

        menu.prepare(child);

        Component newTitle = menu.titleFor(p);
        Inventory previousTop = null;
        GuiMenu currentlyOpen = openMenus.get(p.getUniqueId());
        if (currentlyOpen == menu) {
            previousTop = p.getOpenInventory().getTopInventory();
        }

        Inventory inv = menu.createInventory(newTitle);
        menu.populate(p, inv);

        if (sem == OpenSemantics.REOPEN && previousTop != null) {
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

    /* ---------- Event handlers ---------- */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        GuiMenu menu = openMenus.get(p.getUniqueId());
        if (menu == null) return;

        if (!menu.owns(e.getView().getTopInventory())) return;

        long now = System.currentTimeMillis();
        Long prev = clickDebounce.put(p.getUniqueId(), now);
        if (prev != null && (now - prev) < CLICK_DEBOUNCE_MS) {
            e.setCancelled(true);
            return;
        }

        e.setCancelled(true);

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

        Inventory top = e.getView().getTopInventory();
        if (!menu.owns(top)) return;

        for (int rawSlot : e.getRawSlots()) {
            if (rawSlot < top.getSize()) {
                e.setCancelled(true);
                return;
            }
        }
    }

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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        GuiMenu menu = openMenus.get(p.getUniqueId());
        if (menu == null) return;
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
