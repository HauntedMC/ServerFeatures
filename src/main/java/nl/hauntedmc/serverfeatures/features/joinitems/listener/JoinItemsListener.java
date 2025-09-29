package nl.hauntedmc.serverfeatures.features.joinitems.listener;

import nl.hauntedmc.serverfeatures.common.util.BukkitTime;
import nl.hauntedmc.serverfeatures.features.joinitems.JoinItems;
import nl.hauntedmc.serverfeatures.features.joinitems.internal.JoinItemsHandler;
import nl.hauntedmc.serverfeatures.features.joinitems.model.JoinItemDefinition;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Applies join delay, gives items, enforces protections, and executes commands.
 */
public final class JoinItemsListener implements Listener {

    private final JoinItems feature;
    private final JoinItemsHandler handler;

    public JoinItemsListener(JoinItems feature, JoinItemsHandler handler) {
        this.feature = feature;
        this.handler = handler;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        final var player = e.getPlayer();
        final int delay = handler.getJoinDelayTicks();

        // Defer to allow world/chunks/other plugins to finish first.
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask( () -> {
            if (!player.isOnline()) return;

            if (handler.isRemoveOnJoin()) {
                handler.purgeFor(player);
            }
            handler.giveAll(player);
        }, BukkitTime.ticks(Math.max(0, delay)));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        if (!handler.isRemoveOnLeave()) return;
        handler.purgeFor(e.getPlayer());
    }

    // -----------------------
    // Command execution on use
    // -----------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        // Ignore pressure plates/trampling etc.
        Action action = e.getAction();
        if (action == Action.PHYSICAL) return;

        // Only handle one call (main hand) to avoid double triggers with offhand
        EquipmentSlot which = e.getHand();
        if (!JoinItemsHandler.isMainHand(which)) return;

        // For air clicks, e.getItem() may be null on some versions; read directly from main hand.
        ItemStack item = e.getPlayer().getInventory().getItemInMainHand();

        Optional<JoinItemDefinition> defOpt = handler.definitionOf(item);
        if (defOpt.isEmpty()) return;

        JoinItemDefinition def = defOpt.get();

        // Execute commands as the player
        for (String cmd : def.commands()) {
            if (!cmd.isBlank()) {
                e.getPlayer().performCommand(cmd);
            }
        }

        // Prevent default item use (placing/consuming/activating) regardless of click target (block/air)
        e.setCancelled(true);
    }

    // -----------------------
    // Protections (move/drop)
    // -----------------------

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onDrop(PlayerDropItemEvent e) {
        ItemStack stack = e.getItemDrop().getItemStack();
        Optional<JoinItemDefinition> defOpt = handler.definitionOf(stack);
        if (defOpt.isEmpty()) return;

        JoinItemDefinition def = defOpt.get();
        if (def.undroppable()) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        // Prevent moving to/from offhand
        boolean managedMain = handler.isManaged(e.getMainHandItem());
        boolean managedOff  = handler.isManaged(e.getOffHandItem());
        if (!managedMain && !managedOff) return;

        // If either side is a protected item, block swap
        if (managedMain) {
            Optional<JoinItemDefinition> def = handler.definitionOf(e.getMainHandItem());
            if (def.map(JoinItemDefinition::unmovable).orElse(false)) {
                e.setCancelled(true);
                return;
            }
        }
        if (managedOff) {
            Optional<JoinItemDefinition> def = handler.definitionOf(e.getOffHandItem());
            if (def.map(JoinItemDefinition::unmovable).orElse(false)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent e) {
        ItemStack current = e.getCurrentItem();
        ItemStack cursor  = e.getCursor();

        boolean currentManaged = handler.isManaged(current);
        boolean cursorManaged  = handler.isManaged(cursor);

        // If neither is managed, nothing to do
        if (!currentManaged && !cursorManaged) return;

        // Determine if action would move/alter a protected item
        ClickType click = e.getClick();

        // If the managed item is unmovable, block changes involving it.
        if (currentManaged) {
            if (handler.definitionOf(current).map(JoinItemDefinition::unmovable).orElse(false)) {
                e.setCancelled(true);
                return;
            }
        }
        if (cursorManaged) {
            if (handler.definitionOf(cursor).map(JoinItemDefinition::unmovable).orElse(false)) {
                e.setCancelled(true);
            }
        }

        // Further belt-and-suspenders: block number-key hotbar swaps if source/target is managed+unmovable
        if (click == ClickType.NUMBER_KEY) {
            var inv = e.getWhoClicked().getInventory();
            int hotbarSlot = e.getHotbarButton();
            ItemStack hotbarItem = inv.getItem(hotbarSlot);
            if (handler.definitionOf(hotbarItem).map(JoinItemDefinition::unmovable).orElse(false)) {
                e.setCancelled(true);
            }
        }
    }

    // Prevent block placement with a managed item (e.g., END_CRYSTAL etc.)
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPlace(BlockPlaceEvent e) {
        if (handler.isManaged(e.getItemInHand())) {
            // If the managed item is flagged 'locked', treat as non-placeable utility item
            if (handler.definitionOf(e.getItemInHand()).map(JoinItemDefinition::locked).orElse(false)) {
                e.setCancelled(true);
            }
        }
    }
}
