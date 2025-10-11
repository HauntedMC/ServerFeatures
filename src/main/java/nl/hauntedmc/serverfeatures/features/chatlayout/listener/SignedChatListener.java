package nl.hauntedmc.serverfeatures.features.chatlayout.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
        event.renderer((player, playerName, message, viewer) -> {
            // Build the base formatted message with your ChatLayout (prefix + name + suffix + message)
            Component base = feature.getChatHandler().renderBaseMessage(player, message);

            boolean senderIsStaff = player.hasPermission("staff.remove");

            // Show staff delete control in FRONT when:
            if (viewer instanceof Player staffViewer
                    && staffViewer.hasPermission("staff.remove")
                    && !senderIsStaff) {
                return staffRemoveButton(event).append(Component.space()).append(base);
            }

            // Normal viewers (and staff viewing staff messages) just see the base layout
            return base;
        });
    }

    private Component bracketedX() {
        return Component.text()
                .append(Component.text("❲", NamedTextColor.DARK_GRAY))
                .append(Component.text("✘", NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD))
                .append(Component.text("❳", NamedTextColor.DARK_GRAY))
                .build();
    }

    private Component staffRemoveButton(AsyncChatEvent event) {
        return bracketedX()
                .hoverEvent(Component.text("Delete Message", NamedTextColor.RED))
                .clickEvent(ClickEvent.callback(audience -> Bukkit.getServer().deleteMessage(event.signedMessage())));
    }
}
