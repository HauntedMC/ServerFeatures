package nl.hauntedmc.serverfeatures.features.redistest.listener;

import nl.hauntedmc.serverfeatures.features.redistest.RedisTest;
import nl.hauntedmc.serverfeatures.features.redistest.internal.ChatMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;


public class ChatListener implements Listener {

    private final RedisTest feature;
    private final String serverName;

    public ChatListener(RedisTest feature) {
        this.feature = feature;
        this.serverName = (String) feature.getConfigHandler().getGlobalSetting("server_name");
    }
    
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent e) {
        ChatMessage cm = new ChatMessage(
                serverName,
                e.getPlayer().getName(),
                e.getMessage()
        );

        feature.getEventBusHandler().publishMessage(cm, "mineserver.global");
    }
}
