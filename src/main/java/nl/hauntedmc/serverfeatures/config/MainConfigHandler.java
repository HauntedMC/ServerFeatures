package nl.hauntedmc.serverfeatures.config;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.common.resources.ResourceHandler;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Main config handler (legacy API preserved) + typed/global helpers.
 */
public class MainConfigHandler {
    protected final ResourceHandler configResource;
    protected FileConfiguration config;

    public MainConfigHandler(ServerFeatures plugin) {
        this.configResource = new ResourceHandler(plugin, "config.yml");
        this.config = configResource.getConfig();
        injectGlobalDefaults(Map.of("server_name", "server"));
    }

    public void reloadConfig() {
        configResource.reload();
        this.config = configResource.getConfig();
    }

    public void registerFeature(String featureName) {
        if (!config.contains("features." + featureName)) {
            config.set("features." + featureName + ".enabled", false);
            configResource.save();
        }
    }

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
        if (updated) configResource.save();
    }

    public boolean isFeatureEnabled(String featureName) {
        return config.getBoolean("features." + featureName + ".enabled", false);
    }

    public void setFeatureEnabled(String featureName, boolean enabled) {
        config.set("features." + featureName + ".enabled", enabled);
        configResource.save();
    }

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

    // -------------------------
    // Global setting accessors
    // -------------------------

    /** Legacy raw getter: returns underlying Bukkit value. */
    public Object getGlobalSetting(String key) {
        return config.get("global." + key);
    }

    /** Typed getter: coerces to requested type or throws. */
    public <T> T getGlobalSetting(String key, Class<T> type) {
        return ConfigTypes.convert(getGlobalSetting(key), type);
    }

    /** Typed getter with default. */
    public <T> T getGlobalSetting(String key, Class<T> type, T defaultValue) {
        return ConfigTypes.convertOrDefault(getGlobalSetting(key), type, defaultValue);
    }

    /** A node view rooted at global.<key>. */
    public ConfigNode globalNode(String key) {
        return ConfigNode.ofRaw(getGlobalSetting(key), "global." + key);
    }

    private void injectGlobalDefaults(Map<String, Object> defaultValues) {
        boolean updated = false;
        for (Map.Entry<String, Object> entry : defaultValues.entrySet()) {
            String path = "global." + entry.getKey();
            if (!config.contains(path)) {
                config.set(path, entry.getValue());
                updated = true;
            }
        }
        if (updated) configResource.save();
    }
}
