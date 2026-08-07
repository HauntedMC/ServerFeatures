package nl.hauntedmc.serverfeatures.features.notifylogin.internal;

import org.bukkit.entity.Player;

/** Runtime-only contract for features that change a player's public connection visibility. */
public interface ConnectionVisibilityPort {
    void handleVanishStateChange(Player player, boolean vanished);
}
