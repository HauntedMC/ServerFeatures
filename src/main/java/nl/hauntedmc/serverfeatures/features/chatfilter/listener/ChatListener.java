package nl.hauntedmc.serverfeatures.features.chatfilter.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
        String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
        boolean isFiltered = feature.getChatHandler().applyFilters(event.getPlayer(), rawMessage);
        if (isFiltered) {
            event.setCancelled(true);
        }
    }
}
