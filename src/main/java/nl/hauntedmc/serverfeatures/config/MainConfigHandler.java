package nl.hauntedmc.serverfeatures.config;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.common.resources.ResourceHandler;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class MainConfigHandler {
    protected final ResourceHandler configResource;
    protected FileConfiguration config;

    public MainConfigHandler(ServerFeatures plugin) {
        this.configResource = new ResourceHandler(plugin, "config.yml");
        this.config = configResource.getConfig();
    }

    /**
     * Reloads config from disk using the ResourceHandler.
     */
    public void reloadConfig() {
        configResource.reload();  // Reload from file
        this.config = configResource.getConfig();  // Update the local reference
    }

    /**
     * Registers a feature in config with `enabled: false` if missing.
     */
    public void registerFeature(String featureName) {
        if (!config.contains("features." + featureName)) {
            config.set("features." + featureName + ".enabled", false);
            configResource.save();
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
            configResource.save();
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
        configResource.save();
    }

    /**
     * Cleans up removed features from config.
     */
    public void cleanupUnusedFeatures(Set<String> registeredFeatures) {
        if (config.contains("features")) {
            Set<String> existingKeys = Objects.requireNonNull(config.getConfigurationSection("features")).getKeys(false);
            for (String key : existingKeys) {
                if (!registeredFeatures.contains(key)) {
                    config.set("features." + key, null); // Remove obsolete entries
                }
            }
            configResource.save();
        }
    }
}
