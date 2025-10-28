// File: nl/hauntedmc/serverfeatures/features/parcour/listener/ParcourWandListener.java
package nl.hauntedmc.serverfeatures.features.parcour.listener;

import nl.hauntedmc.serverfeatures.features.parcour.Parcour;
import nl.hauntedmc.serverfeatures.features.parcour.internal.ParcourHandler;
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

public final class ParcourWandListener implements Listener {

    private final Parcour feature;
    private final ParcourHandler handler;

    public ParcourWandListener(Parcour feature, ParcourHandler handler) {
        this.feature = feature;
        this.handler = handler;
    }

    private boolean isWand(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        var meta = stack.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        return pdc.has(handler.wandKey(), PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack inHand = event.getPlayer().getInventory().getItemInMainHand();
        if (!isWand(inHand)) return;

        Player p = event.getPlayer();
        var sel = handler.selection(p);

        if (sel.selectedParcourId == null) {
            p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.select.none").forAudience(p).build());
            return;
        }

        Action action = event.getAction();
        Block clicked = event.getClickedBlock();

        if (action == Action.RIGHT_CLICK_BLOCK || action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            if (clicked == null) return;

            String world = clicked.getWorld().getName();
            int x = clicked.getX(), y = clicked.getY(), z = clicked.getZ();

            if (action == Action.LEFT_CLICK_BLOCK) {
                handler.setPos1(p, clicked.getLocation());
                p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.pos1.set")
                        .with("world", world).with("x", x).with("y", y).with("z", z)
                        .forAudience(p).build());
            } else {
                handler.setPos2(p, clicked.getLocation());
                p.sendMessage(feature.getLocalizationHandler().getMessage("parcour.admin.pos2.set")
                        .with("world", world).with("x", x).with("y", y).with("z", z)
                        .forAudience(p).build());
            }
        }
    }
}
