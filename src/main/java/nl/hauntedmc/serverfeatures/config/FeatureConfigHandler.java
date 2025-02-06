package nl.hauntedmc.serverfeatures.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class FeatureConfigHandler {

    private final FileConfiguration config;
    private final String featureName;

    public FeatureConfigHandler(JavaPlugin plugin, String featureName) {
        this.config = plugin.getConfig();
        this.featureName = featureName;
    }

    /**
     * Get a feature-specific setting.
     */
    public Object getSetting(String key) {
        return config.get("features." + featureName + "." + key);
    }

    /**
     * Get a boolean setting.
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        return config.getBoolean("features." + featureName + "." + key, defaultValue);
    }
}
