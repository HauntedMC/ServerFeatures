package nl.hauntedmc.serverfeatures.framework.command.sync;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public class CommandSync {
    /**
     * Actual sync work (must be called on main).
     */
    public static void apply(Plugin plugin) {
        try {
            Method m = plugin.getServer().getClass().getMethod("syncCommands");
            m.setAccessible(true);
            m.invoke(plugin.getServer());
        } catch (NoSuchMethodException ignored) {
            // Older impls; it's OK to only push to players.
        } catch (Throwable t) {
            plugin.getLogger().warning("syncCommands() failed: " + t.getMessage());
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                p.updateCommands();
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to update commands for " + p.getName() + ": " + t.getMessage());
            }
        }
    }
}
