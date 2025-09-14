package nl.hauntedmc.serverfeatures.features.whitelist.listener;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.features.whitelist.Whitelist;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

public class PlayerLoginListener implements Listener {

    private final Whitelist feature;

    public PlayerLoginListener(Whitelist feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerConnect(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("serverfeatures.feature.whitelist.bypass")) {
            event.setResult(PlayerLoginEvent.Result.ALLOWED);
        } else {
            Component kickMessage = feature.getLocalizationHandler()
                    .getMessage("whitelist.kick_message")
                    .forAudience(player)
                    .build();
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, kickMessage);
        }
    }

}
