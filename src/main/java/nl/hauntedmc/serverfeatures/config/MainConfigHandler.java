package nl.hauntedmc.serverfeatures.config;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.common.resources.ResourceHandler;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class MainConfigHandler {
    protected final ResourceHandler configResource;
    protected FileConfiguration config;

    /**
     * Creates the config handler and ensures global defaults are set.
     */
    public MainConfigHandler(ServerFeatures plugin) {
        this.configResource = new ResourceHandler(plugin, "config.yml");
        this.config = configResource.getConfig();
        injectGlobalDefaults(Map.of("server_name", "server"));
    }

    /**
     * Reloads config from disk and re-applies global defaults.
     */
    public void reloadConfig() {
        configResource.reload();
        this.config = configResource.getConfig();
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
    public void injectFeatureDefaults(String featureName, ConfigMap defaultValues) {
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

    /**
     * Enables or disables a feature.
     */
    public void setFeatureEnabled(String featureName, boolean enabled) {
        config.set("features." + featureName + ".enabled", enabled);
        configResource.save();
    }

    /**
     * Cleans up removed features from config.
     */
    public void cleanupUnusedFeatures(Set<String> registeredFeatures) {
        if (config.contains("features")) {
            Set<String> existingKeys = Objects.requireNonNull(
                    config.getConfigurationSection("features")).getKeys(false);
            for (String key : existingKeys) {
                if (!registeredFeatures.contains(key)) {
                    config.set("features." + key, null);
                }
            }
            configResource.save();
        }
    }

    /**
     * Retrieves a global setting (assumed to exist after initialization).
     *
     * @param key the global setting key (without the "global." prefix)
     * @return the value from config, or null if missing
     */
    public Object getGlobalSetting(String key) {
        return config.get("global." + key);
    }

    /**
     * Injects missing global defaults into the config.
     */
    private void injectGlobalDefaults(Map<String, Object> defaultValues) {
        boolean updated = false;
        for (Map.Entry<String, Object> entry : defaultValues.entrySet()) {
            String path = "global." + entry.getKey();
            if (!config.contains(path)) {
                config.set(path, entry.getValue());
                updated = true;
            }
        }
        if (updated) {
            configResource.save();
        }
    }
}
