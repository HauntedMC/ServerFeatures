package nl.hauntedmc.serverfeatures.features.vanish.listener;

import nl.hauntedmc.serverfeatures.features.vanish.Vanish;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;

public class VisibilityListener implements Listener {

    private final Vanish feature;

    public VisibilityListener(Vanish feature) { this.feature = feature; }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        // Apply DB-persisted state (if any) and notify staff about "joined vanished"
        feature.getService().handleJoin(e);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        // Maintain visibility consistency across worlds
        feature.getService().applyToNewViewer(e.getPlayer());
        feature.getService().allVanished().forEach(id -> {
            if (!e.getPlayer().getUniqueId().equals(id)) {
                var v = e.getPlayer().getServer().getPlayer(id);
                if (v != null) feature.getService().updatePairVisibility(e.getPlayer(), v);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent e) {
        // Reapply hide/show pairwise after respawn
        feature.getService().allVanished().forEach(id -> {
            var v = e.getPlayer().getServer().getPlayer(id);
            if (v != null) feature.getService().updatePairVisibility(e.getPlayer(), v);
        });
        if (feature.getService().isPlayerVanished(e.getPlayer())) {
            // Ensure own flags reapplied
            feature.getService().setVanished(e.getPlayer(), true);
        }
    }
}
