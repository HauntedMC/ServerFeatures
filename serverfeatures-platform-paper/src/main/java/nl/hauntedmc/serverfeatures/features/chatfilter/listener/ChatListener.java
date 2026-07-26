package nl.hauntedmc.serverfeatures.features.chatfilter.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import nl.hauntedmc.serverfeatures.api.util.text.format.ComponentFormatter;
import nl.hauntedmc.serverfeatures.features.chatfilter.ChatFilter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {

    private final ChatFilter feature;

    public ChatListener(ChatFilter feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {
        String rawMessage = ComponentFormatter.serialize(event.message()).format(ComponentFormatter.Serializer.Format.PLAIN).build();
        boolean isFiltered = feature.getChatHandler().applyFilters(event.getPlayer(), rawMessage);
        if (isFiltered) {
            event.setCancelled(true);
        }
    }
}
