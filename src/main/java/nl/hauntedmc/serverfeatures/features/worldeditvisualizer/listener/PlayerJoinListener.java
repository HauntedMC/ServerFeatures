package nl.hauntedmc.serverfeatures.features.worldeditvisualizer.listener;

import nl.hauntedmc.serverfeatures.features.worldeditvisualizer.internal.VisualizationService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerJoinListener implements Listener {

    private final VisualizationService service;

    public PlayerJoinListener(VisualizationService service) {
        this.service = service;
    }
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (service != null && e.getPlayer().hasPermission("serverfeatures.feature.worldeditvisualizer.use")) {
            service.enable(e.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (service != null) {
            service.clear(e.getPlayer());
        }
    }

}
