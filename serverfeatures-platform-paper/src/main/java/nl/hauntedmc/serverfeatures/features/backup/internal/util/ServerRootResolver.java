package nl.hauntedmc.serverfeatures.features.backup.internal.util;

import nl.hauntedmc.serverfeatures.framework.log.FeatureLogger;
import org.bukkit.plugin.Plugin;

import java.io.File;

public final class ServerRootResolver {
    private ServerRootResolver() {
    }

    public static File resolve(Plugin plugin, FeatureLogger log) {
        try {
            File dataFolder = plugin.getDataFolder();
            File pluginsDir = dataFolder.getParentFile();
            if (pluginsDir != null) {
                File parent = pluginsDir.getAbsoluteFile().getParentFile();
                if (parent != null) return parent.getAbsoluteFile();
                return pluginsDir.getAbsoluteFile();
            }
            File wc = plugin.getServer().getWorldContainer();
            return wc.getAbsoluteFile();
        } catch (Throwable t) {
            if (log != null) log.warning("Server root resolution failed: " + t.getMessage());
            return new File(".").getAbsoluteFile();
        }
    }
}
