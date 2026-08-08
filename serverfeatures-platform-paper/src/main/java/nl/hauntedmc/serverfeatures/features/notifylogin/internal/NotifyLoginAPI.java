package nl.hauntedmc.serverfeatures.features.notifylogin.internal;

import nl.hauntedmc.serverfeatures.framework.port.ConnectionVisibilityPort;
import org.bukkit.entity.Player;

import java.util.Objects;

/** Feature-local implementation of the connection visibility integration port. */
public final class NotifyLoginAPI implements ConnectionVisibilityPort {

    private final NotificationHandler notificationHandler;

    public NotifyLoginAPI(NotificationHandler notificationHandler) {
        this.notificationHandler = Objects.requireNonNull(notificationHandler, "notificationHandler");
    }

    @Override
    public void handleVanishStateChange(Player player, boolean vanished) {
        notificationHandler.handleVanishStateChange(player, vanished);
    }
}
