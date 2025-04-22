package nl.hauntedmc.serverfeatures.features.staffchat.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import nl.hauntedmc.serverfeatures.features.staffchat.internal.ChatChannel;
import nl.hauntedmc.serverfeatures.features.staffchat.internal.ChatChannelHandler;
import nl.hauntedmc.serverfeatures.features.staffchat.internal.messaging.StaffChatMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;

import nl.hauntedmc.serverfeatures.features.staffchat.StaffChat;

public class ChatListener implements Listener {

    private final StaffChat feature;
    private final ChatChannelHandler handler;
    private final String serverName;

    public ChatListener(StaffChat feature) {
        this.feature = feature;
        this.handler = feature.getChatChannelHandler();
        this.serverName = (String) feature.getConfigHandler().getSetting("server_name");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message());

        String prefix = rawMessage.substring(0, 1);
        ChatChannel channel = handler.getChannelByPrefix(prefix);
        if (channel == null || !rawMessage.startsWith(prefix)) {
            return;
        }
        if (!player.hasPermission(channel.getPermission())) {
            return;
        }
        String channelMessage = rawMessage.substring(prefix.length()).trim();
        StaffChatMessage scMessage = new StaffChatMessage("staffchat", prefix, channelMessage, event.getPlayer().getName(), serverName);

        event.setCancelled(true);
        feature.getEventBusHandler().publishMessage(scMessage, "proxy.staffchat.message");
    }
}
