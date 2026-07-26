package nl.hauntedmc.serverfeatures.features.vanish.listener;

import nl.hauntedmc.serverfeatures.features.vanish.Vanish;
import nl.hauntedmc.serverfeatures.framework.persistence.DataRegistryIdentityGate;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class VisibilityListener implements Listener {

    private final Vanish feature;

    public VisibilityListener(Vanish feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        DataRegistryIdentityGate.runWhenReady(
                feature,
                player,
                (readyPlayer, identity) -> feature.getService().handleJoin(readyPlayer, identity),
                "vanish join"
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeave(PlayerQuitEvent event) {
        feature.getService().handleLeave(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        feature.getService().applyToNewViewer(event.getPlayer());
        feature.getService().allVanished().forEach(id -> {
            if (!event.getPlayer().getUniqueId().equals(id)) {
                Player vanished = event.getPlayer().getServer().getPlayer(id);
                if (vanished != null) {
                    feature.getService().updatePairVisibility(event.getPlayer(), vanished);
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent event) {
        feature.getService().allVanished().forEach(id -> {
            Player vanished = event.getPlayer().getServer().getPlayer(id);
            if (vanished != null) {
                feature.getService().updatePairVisibility(event.getPlayer(), vanished);
            }
        });
        if (feature.getService().isPlayerVanished(event.getPlayer())) {
            feature.getService().setVanished(event.getPlayer(), true);
        }
    }
}
