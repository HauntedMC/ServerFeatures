package nl.hauntedmc.serverfeatures.features.nickname.listener;

import nl.hauntedmc.serverfeatures.features.nickname.Nickname;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final Nickname feature;

    public PlayerJoinListener(Nickname feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        feature.initializePlayer(event.getPlayer());
    }
}
