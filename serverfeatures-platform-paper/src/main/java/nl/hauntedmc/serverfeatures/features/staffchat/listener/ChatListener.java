package nl.hauntedmc.serverfeatures.features.staffchat.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import nl.hauntedmc.proxyfeatures.contracts.messaging.StaffChatMessage;
import nl.hauntedmc.serverfeatures.api.util.text.format.ComponentFormatter;
import nl.hauntedmc.serverfeatures.features.staffchat.StaffChat;
import nl.hauntedmc.serverfeatures.features.staffchat.internal.ChatChannel;
import nl.hauntedmc.serverfeatures.features.staffchat.internal.ChatChannelHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {

    private final StaffChat feature;
    private final ChatChannelHandler handler;
    private final String serverName;

    public ChatListener(StaffChat feature) {
        this.feature = feature;
        this.handler = feature.getChatChannelHandler();
        this.serverName = (String) feature.getConfigHandler().getGlobalSetting("server_name");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        String rawMessage = ComponentFormatter.serialize(event.message()).format(ComponentFormatter.Serializer.Format.PLAIN).build();

        ChatChannel channel = handler.getChannelForMessage(rawMessage);
        if (channel == null) {
            return;
        }
        if (!player.hasPermission(channel.getPermission())) {
            return;
        }
        String prefix = channel.getPrefix();
        String channelMessage = rawMessage.substring(prefix.length()).trim();
        StaffChatMessage scMessage = new StaffChatMessage(prefix, channelMessage, event.getPlayer().getName(), serverName);

        event.setCancelled(true);
        feature.getEventBusHandler().publishMessage(scMessage, "proxy.staffchat.message");
    }
}
