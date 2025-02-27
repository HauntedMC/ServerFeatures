package nl.hauntedmc.serverfeatures.features.chatlayout.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import nl.hauntedmc.serverfeatures.features.chatlayout.ChatLayout;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {

    private final ChatLayout feature;

    public ChatListener(ChatLayout feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String rawMessage = LegacyComponentSerializer.legacySection().serialize(event.message());
        String chatPrefix = feature.getChatHandler().formatPrefix(player);
        String chatMessage = feature.getChatHandler().formatChatMessage(player, rawMessage);
        feature.getChatHandler().sendModifiedChatMessage(player, chatPrefix, chatMessage, event.viewers());
        event.viewers().clear();
    }
}
