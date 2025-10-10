package nl.hauntedmc.serverfeatures.features.sanitize.internal.util;

import nl.hauntedmc.serverfeatures.framework.log.FeatureLogger;
import org.bukkit.plugin.Plugin;

import java.io.File;

/**
 * Resolves the server root directory defensively across Paper/Spigot variants.
 * Always returns a non-null absolute directory (falls back to CWD).
 */
public final class ServerRootResolver {

    private ServerRootResolver() {}

    public static File resolve(Plugin plugin, FeatureLogger log) {
        try {
            // Preferred: derive from the plugin's data folder: .../plugins/<YourPlugin>/
            // Server root is parent of "plugins" if present.
            File dataFolder = plugin.getDataFolder();
            File pluginsDir = dataFolder.getParentFile(); // .../plugins
            if (pluginsDir != null) {
                File pluginsDirAbs = pluginsDir.getAbsoluteFile();
                File parent = pluginsDirAbs.getParentFile(); // server root if pluginsDir is truly ".../plugins"
                if (parent != null && ("plugins".equalsIgnoreCase(pluginsDirAbs.getName()) || parent.exists())) {
                    return parent.getAbsoluteFile();
                }
                // If parent is null or name isn't "plugins", fall back to pluginsDir itself
                return pluginsDirAbs;
            }

            // Fallback: Bukkit world container (usually server root or a sibling)
            File wc = plugin.getServer().getWorldContainer();
            return wc.getAbsoluteFile();

            // Last resort: current working directory
        } catch (Throwable t) {
            if (log != null) log.warning("Server root resolution failed: " + t.getMessage());
            return new File(".").getAbsoluteFile();
        }
    }
}
