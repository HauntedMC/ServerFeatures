package nl.hauntedmc.serverfeatures.features.balloons.listener;

import nl.hauntedmc.serverfeatures.features.balloons.Balloons;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;

/**
 * Event wiring for balloons (no persistence, no item mode).
 */
public final class BalloonsListener implements Listener {

    private final Balloons feature;

    public BalloonsListener(Balloons feature) {
        this.feature = feature;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Parrot parrot) {
            // Prevent damage to hidden anchors
            if (parrot.getScoreboardTags().contains("ServerFeaturesBalloons")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        feature.getHandler().getActiveBalloon(player).ifPresent(def -> feature.getHandler().handleTeleport(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        feature.getHandler().removeBalloon(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        feature.getHandler().removeBalloon(event.getEntity());
    }

    @EventHandler
    public void onLeash(PlayerLeashEntityEvent event) {
        // Prevent creating leash hitches when balloon is active
        if (feature.getHandler().getActiveBalloon(event.getPlayer()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onUnLeash(PlayerUnleashEntityEvent event) {
        if (event.getEntity() instanceof Parrot parrot) {
            if (parrot.getScoreboardTags().contains("ServerFeaturesBalloons")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInteractArmorStand(PlayerInteractAtEntityEvent event) {
        if (event.getRightClicked() instanceof ArmorStand as) {
            if (as.getScoreboardTags().contains("ServerFeaturesBalloons")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player player) {
            feature.getHandler().removeBalloon(player);
        }
    }
}
