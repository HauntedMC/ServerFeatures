package nl.hauntedmc.serverfeatures.features.graveyard.capture;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GraveDeathListener implements Listener {
    private final GraveCaptureService captureService;
    private final Map<UUID, DeathInventorySnapshot> snapshots = new ConcurrentHashMap<>();

    public GraveDeathListener(GraveCaptureService captureService) {
        this.captureService = captureService;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeathSnapshot(PlayerDeathEvent event) {
        snapshots.put(event.getPlayer().getUniqueId(), DeathInventorySnapshot.capture(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeathFinalize(PlayerDeathEvent event) {
        DeathInventorySnapshot snapshot = snapshots.remove(event.getPlayer().getUniqueId());
        if (snapshot != null) {
            captureService.handleDeath(snapshot, event);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        snapshots.remove(event.getPlayer().getUniqueId());
    }
}
