package nl.hauntedmc.serverfeatures.features.graveyard.listener;

import nl.hauntedmc.serverfeatures.features.graveyard.Graveyard;
import nl.hauntedmc.serverfeatures.features.graveyard.placement.LastSafeLocationTracker;
import nl.hauntedmc.serverfeatures.features.graveyard.runtime.GraveManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

/**
 * Bridges player/world lifecycle changes into the location-anchored grave runtime.
 */
public final class GravePlayerLifecycleListener implements Listener {
    private final Graveyard feature;
    private final GraveManager manager;
    private final LastSafeLocationTracker safeLocations;

    public GravePlayerLifecycleListener(
            Graveyard feature,
            GraveManager manager,
            LastSafeLocationTracker safeLocations
    ) {
        this.feature = feature;
        this.manager = manager;
        this.safeLocations = safeLocations;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        manager.onPlayerJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        manager.onPlayerQuit(event.getPlayer());
        safeLocations.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getFrom().getWorld() != event.getTo().getWorld()
                || event.getFrom().distanceSquared(event.getTo()) >= 64.0 * 64.0) {
            manager.onPlayerTransition(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        manager.onPlayerTransition(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        manager.onPlayerTransition(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        manager.onWorldUnload(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        feature.getLifecycleManager().getTaskManager().scheduleOneTimeTask(
                () -> manager.onWorldLoad(event.getWorld())
        );
    }
}
