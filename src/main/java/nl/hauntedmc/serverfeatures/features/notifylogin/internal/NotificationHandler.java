package nl.hauntedmc.serverfeatures.features.notifylogin.internal;

import de.myzelyam.api.vanish.VanishAPI;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.features.notifylogin.NotifyLogin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class NotificationHandler {
    private final NotifyLogin feature;

    public NotificationHandler(NotifyLogin feature) {
        this.feature = feature;
    }

    public void notify(@NotNull Player player) {
        if (player.hasPermission("serverfeatures.feature.notifylogin.supremeplus")) {
            if (isPlayerVanished(player)) {
                return;
            }
            Component joinMessage = feature.getLocalizationHandler().getMessage("notifylogin.supremeplus", Map.of("name", player.getName()));
            Bukkit.broadcast(joinMessage);
        }
    }

    /**
     * Checks if a player is vanished using PremiumVanish API.
     *
     * @param player The player to check.
     * @return True if the player is vanished, false otherwise.
     */
    private boolean isPlayerVanished(Player player) {
        if (Bukkit.getPluginManager().isPluginEnabled("PremiumVanish")) {
            return VanishAPI.isInvisible(player);
        }
        return false;
    }
}
