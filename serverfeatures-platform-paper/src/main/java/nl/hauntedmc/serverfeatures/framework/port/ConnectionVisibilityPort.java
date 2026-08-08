package nl.hauntedmc.serverfeatures.framework.port;

import org.bukkit.entity.Player;

/**
 * Runtime-only framework port for features that change a player's public connection visibility.
 *
 * <p>This is deliberately Paper-runtime-only; it is not part of the dependency-free public API.</p>
 */
public interface ConnectionVisibilityPort {
    void handleVanishStateChange(Player player, boolean vanished);
}
