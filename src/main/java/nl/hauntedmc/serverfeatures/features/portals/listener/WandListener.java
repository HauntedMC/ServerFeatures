package nl.hauntedmc.serverfeatures.features.portals.listener;

import nl.hauntedmc.serverfeatures.features.portals.Portals;
import nl.hauntedmc.serverfeatures.features.portals.internal.PortalsHandler;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class WandListener implements Listener {

    private final Portals feature;
    private final PortalsHandler handler;
    private final NamespacedKey wandKey;

    public WandListener(Portals feature, PortalsHandler handler) {
        this.feature = feature;
        this.handler = handler;
        this.wandKey = handler.getWandKey();
    }

    private boolean isWand(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        var meta = stack.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        return pdc.has(wandKey, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        // Only respond to main-hand interactions to avoid double-trigger
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack inHand = event.getPlayer().getInventory().getItemInMainHand();
        if (!isWand(inHand)) return;

        Player p = event.getPlayer();
        var sel = handler.selection(p);

        if (sel.selectedPortalId == null) {
            p.sendMessage(feature.getLocalizationHandler().getMessage("portals.wand.select_first").forAudience(p).build());
            return;
        }

        Action action = event.getAction();
        Block clicked = event.getClickedBlock();

        // Prevent block usage/break/interaction while holding the wand
        if (action == Action.RIGHT_CLICK_BLOCK || action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            if (clicked == null) return;

            String world = clicked.getWorld().getName();
            int x = clicked.getX(), y = clicked.getY(), z = clicked.getZ();

            if (action == Action.LEFT_CLICK_BLOCK) {
                handler.setPos1(p, clicked.getLocation());
                p.sendMessage(feature.getLocalizationHandler().getMessage("portals.pos1.set")
                        .with("world", world)
                        .with("x", x)
                        .with("y", y)
                        .with("z", z)
                        .forAudience(p).build());
            } else { // RIGHT_CLICK_BLOCK
                handler.setPos2(p, clicked.getLocation());
                p.sendMessage(feature.getLocalizationHandler().getMessage("portals.pos2.set")
                        .with("world", world)
                        .with("x", x)
                        .with("y", y)
                        .with("z", z)
                        .forAudience(p).build());
            }
        }

    }
}
