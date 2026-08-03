package nl.hauntedmc.serverfeatures.features.autopickup.listener;

import nl.hauntedmc.serverfeatures.features.autopickup.AutoPickup;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class AutoPickupPlayerListener implements Listener {

    private final AutoPickup feature;

    public AutoPickupPlayerListener(AutoPickup feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        feature.preferences().initialize(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        feature.preferences().remove(event.getPlayer());
        feature.clearPlayerDiagnostics(event.getPlayer().getUniqueId());
    }
}
