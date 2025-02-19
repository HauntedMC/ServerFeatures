package nl.hauntedmc.serverfeatures.features.glow.listener;

import nl.hauntedmc.serverfeatures.features.glow.Glow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener to remove glow effects when players log out.
 */
public class GlowListener implements Listener {

    private final Glow feature;

    public GlowListener(Glow feature) {
        this.feature = feature;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        feature.removeGlow(player);
    }
}