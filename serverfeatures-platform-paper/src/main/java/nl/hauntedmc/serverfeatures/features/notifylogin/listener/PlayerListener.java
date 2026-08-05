package nl.hauntedmc.serverfeatures.features.notifylogin.listener;

import nl.hauntedmc.serverfeatures.features.notifylogin.NotifyLogin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Replaces Paper's local connection messages with NotifyLogin-owned messages.
 */
public final class PlayerListener implements Listener {

    private final NotifyLogin feature;

    public PlayerListener(NotifyLogin feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        feature.getNotificationHandler().handleJoin(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        feature.getNotificationHandler().handleQuit(event);
    }
}
