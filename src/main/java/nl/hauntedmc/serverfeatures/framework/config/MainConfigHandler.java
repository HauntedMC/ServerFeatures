// File: MainConfigHandler.java
package nl.hauntedmc.serverfeatures.framework.config;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigTypes;
import nl.hauntedmc.serverfeatures.api.io.resources.ResourceHandler;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Main config handler.
 * Thread-safe met read/write locks.
 */
public class MainConfigHandler {
    protected final ResourceHandler configResource;
    protected FileConfiguration config;
    private final Logger logger;
    protected final ReentrantReadWriteLock rw = new ReentrantReadWriteLock();

    public MainConfigHandler(ServerFeatures plugin) {
        this.configResource = new ResourceHandler(plugin, "config.yml");
        this.config = configResource.getConfig();
        this.logger = plugin.getLogger();
        injectGlobalDefaults(Map.of("server_name", "server"));
    }

    public void reloadConfig() {
        rw.writeLock().lock();
        try {
            configResource.reload();
            this.config = configResource.getConfig();
        } finally {
            rw.writeLock().unlock();
        }
    }

    public void registerFeature(String featureName) {
        boolean changed = false;
        rw.writeLock().lock();
        try {
            if (!config.contains("features." + featureName)) {
                String path = "features." + featureName + ".enabled";
                config.set(path, false);
                changed = true;
                logger.info("[ServerFeatures] [Config] Added missing key '" + path + "' (default=false)");
            }
            if (changed) {
                configResource.save();
            }
        } finally {
            rw.writeLock().unlock();
        }
    }

    public void injectFeatureDefaults(String featureName, ConfigMap defaultValues) {
        final String basePath = "features." + featureName;
        boolean updated = false;

        rw.writeLock().lock();
        try {
            Set<String> allowedTopKeys = topLevelKeysFromDefaults(defaultValues);
            updated |= pruneUnknownFeatureKeys(featureName, allowedTopKeys);
            updated |= reconcileMismatchedFeatureKeyTypes(featureName, defaultValues);

            for (Map.Entry<String, Object> entry : defaultValues.entrySet()) {
                String keyPath = basePath + "." + entry.getKey();
                if (!config.contains(keyPath)) {
                    config.set(keyPath, entry.getValue());
                    updated = true;
                    logger.info("[ServerFeatures] [Config] Added missing key '" + keyPath + "'");
                }
            }

            if (updated) {
                configResource.save();
            }
        } finally {
            rw.writeLock().unlock();
        }
    }

    public boolean isFeatureEnabled(String featureName) {
        rw.readLock().lock();
        try {
            return config.getBoolean("features." + featureName + ".enabled", false);
        } finally {
            rw.readLock().unlock();
        }
    }

    public void setFeatureEnabled(String featureName, boolean enabled) {
        rw.writeLock().lock();
        try {
            String path = "features." + featureName + ".enabled";
            config.set(path, enabled);
            configResource.save();
        } finally {
            rw.writeLock().unlock();
        }
    }

    public void cleanupUnusedFeatures(Set<String> registeredFeatures) {
        rw.writeLock().lock();
        try {
            if (!config.contains("features")) return;

            ConfigurationSection section = config.getConfigurationSection("features");
            if (section == null) return;

            boolean updated = false;
            for (String key : section.getKeys(false)) {
                if (!registeredFeatures.contains(key)) {
                    String path = "features." + key;
                    config.set(path, null);
                    updated = true;
                    logger.info("[ServerFeatures] [Config] Removed unused feature section '" + path + "'");
                }
            }
            if (updated) {
                configResource.save();
            }
        } finally {
            rw.writeLock().unlock();
        }
    }

    public Object getGlobalSetting(String key) {
        rw.readLock().lock();
        try {
            return config.get("global." + key);
        } finally {
            rw.readLock().unlock();
        }
    }

    public <T> T getGlobalSetting(String key, Class<T> type) {
        return ConfigTypes.convert(getGlobalSetting(key), type);
    }

    public <T> T getGlobalSetting(String key, Class<T> type, T defaultValue) {
        return ConfigTypes.convertOrDefault(getGlobalSetting(key), type, defaultValue);
    }

    public ConfigNode globalNode(String key) {
        rw.readLock().lock();
        try {
            return ConfigNode.ofRaw(config.get("global." + key), "global." + key);
        } finally {
            rw.readLock().unlock();
        }
    }

    private void injectGlobalDefaults(Map<String, Object> defaultValues) {
        boolean updated = false;
        rw.writeLock().lock();
        try {
            for (Map.Entry<String, Object> entry : defaultValues.entrySet()) {
                String path = "global." + entry.getKey();
                if (!config.contains(path)) {
                    config.set(path, entry.getValue());
                    updated = true;
                    logger.info("[ServerFeatures] [Config] Added missing global key '" + path + "'");
                }
            }
            if (updated) {
                configResource.save();
            }
        } finally {
            rw.writeLock().unlock();
        }
    }

    private Set<String> topLevelKeysFromDefaults(ConfigMap defaults) {
        Set<String> out = new LinkedHashSet<>();
        for (String key : defaults.keySet()) {
            int dot = key.indexOf('.');
            out.add(dot >= 0 ? key.substring(0, dot) : key);
        }
        return out;
    }

    private boolean pruneUnknownFeatureKeys(String featureName, Set<String> allowedTopLevelKeys) {
        String basePath = "features." + featureName;
        ConfigurationSection section = config.getConfigurationSection(basePath);
        if (section == null) return false;

        boolean changed = false;
        for (String existingKey : section.getKeys(false)) {
            if (!allowedTopLevelKeys.contains(existingKey)) {
                String full = basePath + "." + existingKey;
                config.set(full, null);
                changed = true;
                logger.info("[ServerFeatures] [Config] Removed unknown key '" + full + "'");
            }
        }
        return changed;
    }

    private boolean reconcileMismatchedFeatureKeyTypes(String featureName, ConfigMap defaults) {
        String basePath = "features." + featureName;
        ConfigurationSection section = config.getConfigurationSection(basePath);
        if (section == null) return false;

        boolean changed = false;
        Set<String> existingTopKeys = section.getKeys(false);

        for (String topKey : existingTopKeys) {
            String keyPath = basePath + "." + topKey;
            ConfigValueKind expected = expectedKindForTopKey(topKey, defaults);
            if (expected == null) continue;

            Object existing = config.get(keyPath);
            ConfigValueKind actual = classifyConfigType(existing);
            if (actual == null) continue;

            if (expected != actual) {
                config.set(keyPath, null);
                changed = true;
                logger.info("[ServerFeatures] [Config] Removed key '" + keyPath + "' due to schema change");
            }
        }
        return changed;
    }

    private ConfigValueKind expectedKindForTopKey(String topKey, ConfigMap defaults) {
        if (defaults.contains(topKey)) {
            return classifyConfigType(defaults.get(topKey));
        }
        String prefix = topKey + ".";
        for (String k : defaults.keySet()) {
            if (k.startsWith(prefix)) {
                return ConfigValueKind.MAP;
            }
        }
        return null;
    }

    private enum ConfigValueKind {MAP, LIST, BOOLEAN, NUMBER, STRING, OTHER}

    private ConfigValueKind classifyConfigType(Object value) {
        return switch (value) {
            case null -> null;
            case ConfigurationSection ignored -> ConfigValueKind.MAP;
            case Map<?, ?> ignored -> ConfigValueKind.MAP;
            case List<?> ignored -> ConfigValueKind.LIST;
            case Boolean ignored -> ConfigValueKind.BOOLEAN;
            case Number ignored -> ConfigValueKind.NUMBER;
            case CharSequence ignored -> ConfigValueKind.STRING;
            default -> ConfigValueKind.OTHER;
        };
    }

    public void mutateAndSave(Consumer<FileConfiguration> mutator) {
        rw.writeLock().lock();
        try {
            mutator.accept(config);
            configResource.save();
        } finally {
            rw.writeLock().unlock();
        }
    }
}
