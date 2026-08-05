package nl.hauntedmc.serverfeatures.features.notifylogin.internal;

import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Optional integration API for features that change a player's public connection visibility.
 */
public final class NotifyLoginAPI {

    private final NotificationHandler notificationHandler;

    public NotifyLoginAPI(NotificationHandler notificationHandler) {
        this.notificationHandler = Objects.requireNonNull(notificationHandler, "notificationHandler");
    }

    /**
     * Applies the configured synthetic leave or join message for an explicit vanish state change.
     *
     * @param player the player whose public visibility changed
     * @param vanished {@code true} when the player entered vanish, {@code false} when they left vanish
     */
    public void handleVanishStateChange(Player player, boolean vanished) {
        notificationHandler.handleVanishStateChange(player, vanished);
    }
}
