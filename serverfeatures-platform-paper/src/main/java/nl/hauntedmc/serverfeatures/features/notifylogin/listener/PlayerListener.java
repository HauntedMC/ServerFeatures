package nl.hauntedmc.serverfeatures.features.notifylogin.listener;

import nl.hauntedmc.serverfeatures.features.notifylogin.NotifyLogin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerListener implements Listener {

    private final NotifyLogin feature;

    public PlayerListener(NotifyLogin feature) {
        this.feature = feature;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        feature.getNotificationHandler().notify(event.getPlayer());
    }

}
