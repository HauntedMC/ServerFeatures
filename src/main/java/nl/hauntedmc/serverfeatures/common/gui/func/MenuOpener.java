package nl.hauntedmc.serverfeatures.common.gui.func;

import org.bukkit.entity.Player;

/** Tiny interface to expose an 'open' entrypoint from features/commands. */
@FunctionalInterface
public interface MenuOpener {
    void open(Player player);
}
