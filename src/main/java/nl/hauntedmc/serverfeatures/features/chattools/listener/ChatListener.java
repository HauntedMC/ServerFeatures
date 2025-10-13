package nl.hauntedmc.serverfeatures.features.chattools.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import nl.hauntedmc.serverfeatures.features.chattools.ChatTools;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public class ChatListener implements Listener {

    private final ChatTools feature;

    public ChatListener(ChatTools feature) {
        this.feature = feature;
    }

    @EventHandler
    public void onAsyncChat(@NotNull AsyncChatEvent event) {
        if (!feature.isChatLocked()) return;

        if (event.getPlayer().hasPermission("serverfeatures.feature.chattools.bypass")) return;

        event.setCancelled(true);
        event.getPlayer().sendMessage(feature.getLocalizationHandler()
                .getMessage("chattools.locked_cant_chat")
                .forAudience(event.getPlayer())
                .build());
    }
}
