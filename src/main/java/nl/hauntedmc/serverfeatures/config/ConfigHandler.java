package nl.hauntedmc.serverfeatures.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Map;
import java.util.Set;

public class ConfigHandler {
    private final JavaPlugin plugin;
    private FileConfiguration config;

    public ConfigHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        plugin.saveDefaultConfig();
    }

    /**
     * Reloads config from disk.
     */
    public void reloadConfig() {
        plugin.reloadConfig();  // Reload from file
        this.config = plugin.getConfig();  // Update the local reference
    }


    /**
     * Registers a feature in config with `enabled: false` if missing.
     */
    public void registerFeature(String featureName) {
        if (!config.contains("features." + featureName)) {
            config.set("features." + featureName + ".enabled", false);
            plugin.saveConfig();
        }
    }

    /**
     * Injects missing default settings for a specific feature.
     */
    public void injectFeatureDefaults(String featureName, Map<String, Object> defaultValues) {
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
     * Checks if a feature is enabled.
     */
    public boolean isFeatureEnabled(String featureName) {
        return config.getBoolean("features." + featureName + ".enabled", false);
    }

    public void setFeatureEnabled(String featureName, boolean enabled) {
        config.set("features." + featureName + ".enabled", enabled);
        plugin.saveConfig();
    }

    /**
     * Cleans up removed features from config.
     */
    public void cleanupUnusedFeatures(Set<String> registeredFeatures) {
        if (config.contains("features")) {
            Set<String> existingKeys = config.getConfigurationSection("features").getKeys(false);
            for (String key : existingKeys) {
                if (!registeredFeatures.contains(key)) {
                    config.set("features." + key, null); // Remove obsolete entries
                }
            }
            plugin.saveConfig();
        }
    }
}
