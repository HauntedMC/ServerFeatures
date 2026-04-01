package nl.hauntedmc.serverfeatures.framework.config;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigService;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigView;

import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Specialized handler for config.yml with schema helpers.
 * Shares the same typed CRUD/batch API via ConfigView.
 */
public class MainConfigHandler extends ConfigView {

    private final Logger logger;

    public MainConfigHandler(ServerFeatures plugin) {
        super(new ConfigService(plugin).open("config.yml", true), "");
        this.logger = plugin.getLogger();
        injectGlobalDefaults(Map.of("server_name", "server"));
    }

    /** Optional: if you already created a ConfigService elsewhere, use it here. */
    public MainConfigHandler(ServerFeatures plugin, ConfigService service) {
        super(service.open("config.yml", true), "");
        this.logger = plugin.getLogger();
        injectGlobalDefaults(Map.of("server_name", "server"));
    }

    public void reloadConfig() { file.reload(); }

    public void registerFeature(String featureName) {
        String path = "features." + featureName + ".enabled";
        if (putIfAbsent(path, false)) {
            logger.info("[ServerFeatures] [Config] Added missing key '" + path + "' (default=false)");
        }
    }

    public void injectFeatureDefaults(String featureName, ConfigMap defaults) {
        final String base = "features." + featureName;

        // prune unknown + reconcile type changes
        Set<String> allowedTop = topLevelKeysFromDefaults(defaults);
        pruneUnknownFeatureKeys(base, allowedTop);
        reconcileMismatchedFeatureKeyTypes(base, defaults);

        // add missing defaults
        defaults.forEach((k, v) -> {
            String path = base + "." + k;
            if (putIfAbsent(path, v)) {
                logger.info("[ServerFeatures] [Config] Added missing key '" + path + "'");
            }
        });
    }

    public boolean isFeatureEnabled(String featureName) {
        return get("features." + featureName + ".enabled", Boolean.class, false);
    }

    public void setFeatureEnabled(String featureName, boolean enabled) {
        put("features." + featureName + ".enabled", enabled);
    }

    public void cleanupUnusedFeatures(Set<String> registeredFeatures) {
        ConfigNode features = node("features");
        for (String k : features.keys()) {
            if (!registeredFeatures.contains(k)) {
                remove("features." + k);
                logger.info("[ServerFeatures] [Config] Removed unused feature section 'features." + k + "'");
            }
        }
    }

    public Object getGlobalSetting(String key) { return get("global." + key); }
    public <T> T getGlobalSetting(String key, Class<T> type) { return get("global." + key, type); }
    public <T> T getGlobalSetting(String key, Class<T> type, T def) { return get("global." + key, type, def); }
    public ConfigNode globalNode(String key) { return node("global." + key); }

    // -------- helpers --------

    private void injectGlobalDefaults(Map<String, Object> defaults) {
        defaults.forEach((k, v) -> {
            String path = "global." + k;
            if (putIfAbsent(path, v)) {
                logger.info("[ServerFeatures] [Config] Added missing global key '" + path + "'");
            }
        });
    }

    private Set<String> topLevelKeysFromDefaults(ConfigMap defaults) {
        return FeatureConfigSchema.topLevelKeysFromDefaults(defaults);
    }

    private FeatureConfigSchema.Kind expectedKindForTopKey(String topKey, ConfigMap defaults) {
        return FeatureConfigSchema.expectedKindForTopKey(topKey, defaults);
    }

    private void pruneUnknownFeatureKeys(String base, Set<String> allowedTop) {
        ConfigNode section = node(base);
        for (String existingTop : section.keys()) {
            if (!allowedTop.contains(existingTop)) {
                remove(base + "." + existingTop);
                logger.info("[ServerFeatures] [Config] Removed unknown key '" + base + "." + existingTop + "'");
            }
        }
    }

    private void reconcileMismatchedFeatureKeyTypes(String base, ConfigMap defaults) {
        ConfigNode section = node(base);
        for (String topKey : section.keys()) {
            String path = base + "." + topKey;
            FeatureConfigSchema.Kind expected = expectedKindForTopKey(topKey, defaults);
            if (expected == null) continue;

            Object existing = node(path).raw();
            FeatureConfigSchema.Kind actual = FeatureConfigSchema.classify(existing);
            if (actual != null && expected != actual) {
                remove(path);
                logger.info("[ServerFeatures] [Config] Removed key '" + path + "' due to schema change");
            }
        }
    }
}
