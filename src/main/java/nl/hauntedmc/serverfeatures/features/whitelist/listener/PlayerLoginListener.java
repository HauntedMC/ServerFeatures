package nl.hauntedmc.serverfeatures.features.whitelist.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

public class PlayerLoginListener implements Listener {

    public PlayerLoginListener() {
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onKick(PlayerLoginEvent event) {
        if (event.getResult() == PlayerLoginEvent.Result.KICK_WHITELIST) {
            Player player = event.getPlayer();
            if (player.hasPermission("serverfeatures.feature.whitelist.bypass")) {
                event.setResult(PlayerLoginEvent.Result.ALLOWED);
            }
        }
    }
}
