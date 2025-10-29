package nl.hauntedmc.serverfeatures.features.parcour.listener;

import nl.hauntedmc.serverfeatures.features.parcour.internal.ParcourHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class ParcourItemListener implements Listener {

    private final ParcourHandler handler;

    public ParcourItemListener(ParcourHandler handler) {
        this.handler = handler;
    }

    private boolean isLeaveItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        var pdc = stack.getItemMeta().getPersistentDataContainer();
        return pdc.has(handler.leaveKey(), PersistentDataType.BYTE);
    }

    private boolean isCheckpointItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        var pdc = stack.getItemMeta().getPersistentDataContainer();
        return pdc.has(handler.checkpointKey(), PersistentDataType.BYTE);
    }

    private boolean isKitItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        var pdc = stack.getItemMeta().getPersistentDataContainer();
        return pdc.has(handler.kitKey(), PersistentDataType.BYTE);
    }

    private boolean isSpecial(ItemStack stack) {
        return isLeaveItem(stack) || isCheckpointItem(stack) || isKitItem(stack);
    }

    /**
     * Make right-click in AIR work and avoid missing the event when another plugin cancels it.
     * We listen even if cancelled and handle both main-hand and off-hand distinctly.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        Player p = event.getPlayer();
        if (!handler.isPlaying(p)) return;

        // Handle per hand to avoid double-triggers and to be robust when getItem() is null
        if (event.getHand() == EquipmentSlot.HAND) {
            ItemStack main = p.getInventory().getItemInMainHand();
            if (isLeaveItem(main)) {
                event.setCancelled(true);
                p.performCommand("parcour leave");
                return;
            }
            if (isCheckpointItem(main)) {
                event.setCancelled(true);
                p.performCommand("parcour checkpoint");
            }
        } else if (event.getHand() == EquipmentSlot.OFF_HAND) {
            ItemStack off = p.getInventory().getItemInOffHand();
            if (isLeaveItem(off)) {
                event.setCancelled(true);
                p.performCommand("parcour leave");
                return;
            }
            if (isCheckpointItem(off)) {
                event.setCancelled(true);
                p.performCommand("parcour checkpoint");
            }
        }
    }

    /**
     * Prevent dropping the special items (Q / Ctrl+Q).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        var stack = event.getItemDrop().getItemStack();
        if (isSpecial(stack) && handler.isPlaying(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent swapping to/off the off-hand (F).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();
        if (!handler.isPlaying(p)) return;

        if (isSpecial(event.getMainHandItem()) || isSpecial(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    /**
     * Block moving special items via clicks: number keys, shift-click, pick-up/put-down, swap off-hand, etc.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (!handler.isPlaying(p)) return;

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        // Block any attempt where the clicked or cursor item is special
        if (isSpecial(current) || isSpecial(cursor)) {
            event.setCancelled(true);
            return;
        }

        // Prevent number-key swaps involving a special item in the hotbar target
        if (event.getClick() == ClickType.NUMBER_KEY) {
            int hotbar = event.getHotbarButton();
            if (hotbar >= 0 && hotbar <= 8) {
                ItemStack hotbarItem = p.getInventory().getItem(hotbar);
                if (isSpecial(hotbarItem)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // Prevent using the "swap to offhand" click type from inventories
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            ItemStack off = p.getInventory().getItemInOffHand();
            if (isSpecial(off) || isSpecial(current) || isSpecial(cursor)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Block dragging the special items around (left/right mouse drag).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (!handler.isPlaying(p)) return;

        // The item being dragged is the old cursor in Bukkit drag events
        ItemStack dragged = event.getOldCursor();
        if (isSpecial(dragged)) {
            event.setCancelled(true);
        }
    }
}
