package nl.hauntedmc.serverfeatures.features.chatlayout.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.serverfeatures.features.chatlayout.ChatLayout;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class SignedChatListener implements Listener {

    private final ChatLayout feature;

    public SignedChatListener(ChatLayout feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        // Use Paper's chat renderer so we don't cancel or manually send audiences.
        event.renderer((sender, playerName, message, audience) -> {
            // Build the base formatted message with your ChatLayout (prefix + name + suffix + message)
            Component base = feature.getChatHandler().renderBaseMessage(sender, message);

            boolean senderIsStaff = sender.hasPermission("staff.remove");

            // Show staff delete control in FRONT when:
            if (audience instanceof Player viewer)
                if (viewer.hasPermission("admin.remove")) {
                    return staffRemoveButton(base, event.signedMessage());
                } else if (viewer.hasPermission("staff.remove") && (!senderIsStaff || sender == viewer)) {
                    return staffRemoveButton(base, event.signedMessage());
                }
            // Normal viewers (and staff viewing staff messages) just see the base layout
            return base;
        });
    }

    private Component staffRemoveButton(Component base, SignedMessage signedMessage) {
        return base.hoverEvent(Component.text("Delete Message", NamedTextColor.RED)).clickEvent(ClickEvent.callback(audience -> Bukkit.getServer().deleteMessage(signedMessage)));
    }
}
