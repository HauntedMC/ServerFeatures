package nl.hauntedmc.serverfeatures.config;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.common.resources.ResourceHandler;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.logging.Logger;

/**
 * Main config handler.
 */
public class MainConfigHandler {
    protected final ResourceHandler configResource;
    protected FileConfiguration config;
    private final Logger logger;

    public MainConfigHandler(ServerFeatures plugin) {
        this.configResource = new ResourceHandler(plugin, "config.yml");
        this.config = configResource.getConfig();
        this.logger = plugin.getLogger();
        injectGlobalDefaults(Map.of("server_name", "server"));
    }

    public void reloadConfig() {
        configResource.reload();
        this.config = configResource.getConfig();
    }

    public void registerFeature(String featureName) {
        if (!config.contains("features." + featureName)) {
            String path = "features." + featureName + ".enabled";
            config.set(path, false);
            configResource.save();
            logger.info("[ServerFeatures] [Config] Added missing key '" + path + "' (default=false)");
        }
    }

    /**
     * Injects defaults for a feature, prunes unknown top-level keys, and resets keys whose top-level type changed.
     * Supports dotted defaults (e.g. "anti_afk.enabled"). A dotted default implies the parent ("anti_afk")
     * is an object/map and must be preserved (not pruned).
     */
    public void injectFeatureDefaults(String featureName, ConfigMap defaultValues) {
        final String basePath = "features." + featureName;
        boolean updated = false;

        // Build the set of allowed top-level keys from defaults, respecting dotted keys (e.g. "anti_afk.enabled" -> "anti_afk")
        Set<String> allowedTopKeys = topLevelKeysFromDefaults(defaultValues);

        // 1) Remove unknown top-level keys (log removals)
        updated |= pruneUnknownFeatureKeys(featureName, allowedTopKeys);

        // 2) Remove keys whose top-level type changed (log removals)
        updated |= reconcileMismatchedFeatureKeyTypes(featureName, defaultValues);

        // 3) Inject missing defaults (log additions)
        for (Map.Entry<String, Object> entry : defaultValues.entrySet()) {
            String keyPath = basePath + "." + entry.getKey(); // supports dotted paths
            if (!config.contains(keyPath)) {
                config.set(keyPath, entry.getValue());
                updated = true;
                logger.info("[ServerFeatures] [Config] Added missing key '" + keyPath + "'");
            }
        }

        if (updated) {
            configResource.save();
        }
    }

    public boolean isFeatureEnabled(String featureName) {
        return config.getBoolean("features." + featureName + ".enabled", false);
    }

    public void setFeatureEnabled(String featureName, boolean enabled) {
        String path = "features." + featureName + ".enabled";
        config.set(path, enabled);
        configResource.save();
    }

    public void cleanupUnusedFeatures(Set<String> registeredFeatures) {
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

    // =========================
    // Global defaults injection
    // =========================

    private void injectGlobalDefaults(Map<String, Object> defaultValues) {
        boolean updated = false;
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
    }

    // =====================================
    // Helpers: pruning & type reconciliation
    // =====================================

    /**
     * Compute the set of allowed top-level keys from defaults.
     * For dotted keys like "anti_afk.enabled", the top-level "anti_afk" is considered allowed.
     */
    private Set<String> topLevelKeysFromDefaults(ConfigMap defaults) {
        Set<String> out = new LinkedHashSet<>();
        for (String key : defaults.keySet()) {
            int dot = key.indexOf('.');
            out.add(dot >= 0 ? key.substring(0, dot) : key);
        }
        return out;
    }

    /**
     * Removes top-level settings under features.<featureName> that are NOT present in the provided allowedTopLevelKeys.
     * This does NOT descend into nested structures; lists/maps under kept keys are left untouched.
     *
     * @return true if any keys were removed; false otherwise
     */
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

    /**
     * For each EXISTING top-level key under features.<featureName>, if its type differs from what defaults imply,
     * remove the existing entry so new defaults can be injected cleanly.
     * Rules:
     *  - If defaults contain an exact top-level key (e.g. "foo"), use its type.
     *  - Else if defaults contain any dotted child "foo.*", then expected type for "foo" is MAP.
     *  - Only compares the top-level node type; it does NOT recurse into nested structures.
     */
    private boolean reconcileMismatchedFeatureKeyTypes(String featureName, ConfigMap defaults) {
        String basePath = "features." + featureName;
        ConfigurationSection section = config.getConfigurationSection(basePath);
        if (section == null) return false;

        boolean changed = false;
        Set<String> existingTopKeys = section.getKeys(false);

        for (String topKey : existingTopKeys) {
            String keyPath = basePath + "." + topKey;

            // Determine expected kind from defaults
            ConfigValueKind expected = expectedKindForTopKey(topKey, defaults);
            if (expected == null) {
                // No expectation (unknown key). pruneUnknownFeatureKeys handles removal/logging already.
                continue;
            }

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

    /** Determine expected top-level kind based on defaults (handles dotted keys). */
    private ConfigValueKind expectedKindForTopKey(String topKey, ConfigMap defaults) {
        // Exact top-level entry takes precedence
        if (defaults.contains(topKey)) {
            return classifyConfigType(defaults.get(topKey));
        }
        // Otherwise, if any default key starts with "topKey."
        String prefix = topKey + ".";
        for (String k : defaults.keySet()) {
            if (k.startsWith(prefix)) {
                return ConfigValueKind.MAP;
            }
        }
        return null; // no expectation
    }

    private enum ConfigValueKind { MAP, LIST, BOOLEAN, NUMBER, STRING, OTHER }

    /** Classifies a raw config object into a coarse type (Bukkit ConfigurationSection counts as MAP). */
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
}
