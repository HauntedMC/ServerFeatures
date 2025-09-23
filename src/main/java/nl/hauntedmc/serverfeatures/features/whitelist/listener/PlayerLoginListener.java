package nl.hauntedmc.serverfeatures.features.whitelist.listener;

import nl.hauntedmc.serverfeatures.features.whitelist.Whitelist;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerLoginListener implements Listener {

    private final Whitelist feature;

    public PlayerLoginListener(Whitelist feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        if (!player.hasPermission("serverfeatures.feature.whitelist.bypass")) {
            var kickMessage = feature.getLocalizationHandler()
                    .getMessage("whitelist.kick_message")
                    .forAudience(player)
                    .build();
            event.joinMessage(null);
            player.kick(kickMessage);
        }
    }


}
