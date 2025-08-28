package nl.hauntedmc.serverfeatures.features.vanish.listener;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.features.vanish.Vanish;
import org.bukkit.Bukkit;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.inventory.Inventory;

public class InteractionListener implements Listener {

    private final Vanish feature;

    public InteractionListener(Vanish feature) { this.feature = feature; }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(boolean) feature.getConfigHandler().getSetting("prevent_damage_and_interact")) return;

        if (e.getEntity() instanceof Player victim && feature.getService().isPlayerVanished(victim)) {
            e.setCancelled(true); return;
        }
        if (e.getDamager() instanceof Player damager && feature.getService().isPlayerVanished(damager)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!(boolean) feature.getConfigHandler().getSetting("prevent_damage_and_interact")) return;

        if (e.getRightClicked() instanceof Player p && feature.getService().isPlayerVanished(p)) {
            e.setCancelled(true); return;
        }
        if (feature.getService().isPlayerVanished(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    /**
     * Optional: silently open containers while vanished by showing a snapshot inventory.
     * This prevents block open animations/sounds because the actual block interaction is cancelled.
     * Changes made in this view do NOT persist to the real container.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockInteract(PlayerInteractEvent e) {
        if (!(boolean) feature.getConfigHandler().getSetting("silent_container_open")) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;
        if (!feature.getService().isPlayerVanished(e.getPlayer())) return;

        var state = e.getClickedBlock().getState();
        if (state instanceof Container container) {
            // Cancel the real interaction to avoid sounds/animation
            e.setCancelled(true);

            // Open a snapshot copy of the container for the vanished player
            Inventory source = container.getInventory();
            Inventory copy = Bukkit.createInventory(e.getPlayer(), source.getSize(), Component.text("Silent Container"));
            copy.setContents(source.getContents());
            e.getPlayer().openInventory(copy);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (!(boolean) feature.getConfigHandler().getSetting("prevent_entity_targeting")) return;

        if (e.getTarget() instanceof Player p && feature.getService().isPlayerVanished(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(boolean) feature.getConfigHandler().getSetting("prevent_item_pickup")) return;

        if (e.getEntity() instanceof Player p && feature.getService().isPlayerVanished(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArrowPickup(PlayerPickupArrowEvent e) {
        if (!(boolean) feature.getConfigHandler().getSetting("prevent_item_pickup")) return;

        if (feature.getService().isPlayerVanished(e.getPlayer())) {
            e.setCancelled(true);
        }
    }
}
