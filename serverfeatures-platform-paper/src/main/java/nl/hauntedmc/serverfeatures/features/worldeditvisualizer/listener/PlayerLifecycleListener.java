package nl.hauntedmc.serverfeatures.features.worldeditvisualizer.listener;

import nl.hauntedmc.serverfeatures.features.worldeditvisualizer.internal.VisualizationService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Reconciles packet-only visuals with the player connection and world lifecycle. */
public final class PlayerLifecycleListener implements Listener {

    private static final String USE_PERMISSION = "serverfeatures.feature.worldeditvisualizer.use";
    private final VisualizationService service;

    public PlayerLifecycleListener(VisualizationService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (event.getPlayer().hasPermission(USE_PERMISSION)) {
            service.enable(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        service.invalidate(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        service.invalidate(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        service.handleQuit(event.getPlayer());
    }
}
