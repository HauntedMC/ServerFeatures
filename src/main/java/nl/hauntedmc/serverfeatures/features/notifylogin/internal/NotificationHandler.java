package nl.hauntedmc.serverfeatures.features.notifylogin.internal;

import nl.hauntedmc.serverfeatures.api.APIRegistry;
import nl.hauntedmc.serverfeatures.features.notifylogin.NotifyLogin;
import nl.hauntedmc.serverfeatures.features.vanish.internal.VanishAPI;
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
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(feature.getLocalizationHandler().getMessage("notifylogin.supremeplus")
                        .forAudience(p)
                        .with("name", player.getName())
                        .build());
            }
        }
    }

    /**
     * Checks if a player is vanished using PremiumVanish API.
     *
     * @param player The player to check.
     * @return True if the player is vanished, false otherwise.
     */
    private boolean isPlayerVanished(Player player) {
        return APIRegistry.get(VanishAPI.class).map(api -> api.isVanished(player.getUniqueId())).orElse(false);
    }
}
