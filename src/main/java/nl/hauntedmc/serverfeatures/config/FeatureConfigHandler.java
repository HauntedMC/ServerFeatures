package nl.hauntedmc.serverfeatures.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Map;

public class FeatureConfigHandler {

    private final JavaPlugin plugin;
    private final FileConfiguration config;
    private final String featureName;

    public FeatureConfigHandler(JavaPlugin plugin, String featureName) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.featureName = featureName;
    }

    /**
     * Injects missing default settings for the feature.
     */
    public void injectDefaults(Map<String, Object> defaultValues) {
        String basePath = "features." + featureName;

        boolean updated = false;
        for (Map.Entry<String, Object> entry : defaultValues.entrySet()) {
            String keyPath = basePath + "." + entry.getKey();
            if (!config.contains(keyPath)) {
                config.set(keyPath, entry.getValue());
                updated = true;
            }
        }

        if (updated) {
            plugin.saveConfig();
        }
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
