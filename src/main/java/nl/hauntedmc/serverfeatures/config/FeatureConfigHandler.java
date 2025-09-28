package nl.hauntedmc.serverfeatures.config;

import nl.hauntedmc.serverfeatures.ServerFeatures;

import java.util.List;
import java.util.Map;

/**
 * Feature-level config handler.
 * - Legacy raw: getSetting(key)
 * - Typed: getSetting(key, Class<T>) and with default
 * - High-level: node() / node(key) / nodeAt(path) for painless nested parsing
 */
public class FeatureConfigHandler extends MainConfigHandler {

    private final String featureName;

    public FeatureConfigHandler(ServerFeatures plugin, String featureName) {
        super(plugin);
        this.featureName = featureName;
    }

    // -------------------------
    // Legacy + typed getters
    // -------------------------

    /** Legacy raw getter: returns underlying Bukkit value under features.<feature>.<key>. */
    public Object getSetting(String key) {
        return config.get("features." + featureName + "." + key);
    }

    /** Typed getter: coerces to requested type or throws. */
    public <T> T getSetting(String key, Class<T> type) {
        return ConfigTypes.convert(getSetting(key), type);
    }

    /** Typed getter with default. */
    public <T> T getSetting(String key, Class<T> type, T defaultValue) {
        return ConfigTypes.convertOrDefault(getSetting(key), type, defaultValue);
    }

    // -------------------------
    // Convenience for maps/lists
    // -------------------------

    public Map<String, Object> getMap(String key) {
        return getSetting(key, Map.class);
    }

    public Map<String, Object> getMap(String key, Map<String, Object> def) {
        return getSetting(key, Map.class, def);
    }

    public <T> List<T> getList(String key, Class<T> elemType) {
        Object raw = getSetting(key);
        return ConfigTypes.convertList(raw, elemType);
    }

    public <T> List<T> getList(String key, Class<T> elemType, List<T> def) {
        try {
            return ConfigTypes.convertList(getSetting(key), elemType);
        } catch (Exception ignored) {
            return def;
        }
    }

    public <V> Map<String, V> getMapValues(String key, Class<V> valueType) {
        return ConfigTypes.convertMapValues(getSetting(key), valueType);
    }

    public <V> Map<String, V> getMapValues(String key, Class<V> valueType, Map<String, V> def) {
        try {
            return ConfigTypes.convertMapValues(getSetting(key), valueType);
        } catch (Exception ignored) {
            return def;
        }
    }

    // -------------------------
    // Node API (zero boilerplate)
    // -------------------------

    /** Node rooted at 'features.<featureName>'. */
    public ConfigNode node() {
        return ConfigNode.ofRaw(config.getConfigurationSection("features." + featureName), "features." + featureName);
    }

    /** Node rooted at 'features.<featureName>.<key>'. */
    public ConfigNode node(String key) {
        return ConfigNode.ofRaw(getSetting(key), "features." + featureName + "." + key);
    }

    /** Node rooted at a dotted path under this feature (e.g., "items.cosmetic-item"). */
    public ConfigNode nodeAt(String dottedPath) {
        return node().getAt(dottedPath);
    }

    /** Typed value at dotted path (throws if invalid). */
    public <T> T getAt(String dottedPath, Class<T> type) {
        return node().getAt(dottedPath).asRequired(type);
    }

    /** Typed value at dotted path with default. */
    public <T> T getAt(String dottedPath, Class<T> type, T defaultValue) {
        return node().getAt(dottedPath).as(type, defaultValue);
    }
}
