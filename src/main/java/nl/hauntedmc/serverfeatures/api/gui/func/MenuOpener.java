package nl.hauntedmc.serverfeatures.api.gui.func;

import org.bukkit.entity.Player;

/**
 * Small functional interface to expose a consistent "open" entry point.
 * Useful for wiring commands or buttons that should open a particular menu.
 */
@FunctionalInterface
public interface MenuOpener {
    void open(Player player);
}
