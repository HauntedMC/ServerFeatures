package nl.hauntedmc.serverfeatures.features.chatlog.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import nl.hauntedmc.serverfeatures.api.util.text.format.ComponentFormatter;
import nl.hauntedmc.serverfeatures.features.chatlog.ChatLog;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {

    private final ChatLog feature;

    public ChatListener(ChatLog feature) {
        this.feature = feature;
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {
        String rawMessage = ComponentFormatter.serialize(event.message()).format(ComponentFormatter.Serializer.Format.PLAIN).build();
        Player player = event.getPlayer();
        feature.getReportHandler().logMessage(player, rawMessage);
    }

}
