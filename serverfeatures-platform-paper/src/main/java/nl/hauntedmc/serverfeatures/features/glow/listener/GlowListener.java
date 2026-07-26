package nl.hauntedmc.serverfeatures.features.glow.listener;

import nl.hauntedmc.serverfeatures.features.glow.Glow;
import nl.hauntedmc.serverfeatures.framework.persistence.DataRegistryIdentityGate;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener to restore glow on join and remove transiently on quit.
 */
public class GlowListener implements Listener {

    private final Glow feature;

    public GlowListener(Glow feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        DataRegistryIdentityGate.runWhenReady(
                feature,
                player,
                (readyPlayer, identity) -> feature.getGlowStateService().restoreGlowFor(readyPlayer, identity),
                "glow restore"
        );
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        feature.getGlowHandler().removeGlowTransient(event.getPlayer());
    }
}
