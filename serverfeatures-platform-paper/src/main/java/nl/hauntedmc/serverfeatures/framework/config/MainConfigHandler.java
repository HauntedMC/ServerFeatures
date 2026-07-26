package nl.hauntedmc.serverfeatures.framework.config;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigService;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigView;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Global config handler for {@code config.yml} and entry point for feature-scoped configs.
 */
public final class MainConfigHandler extends ConfigView {

    private final Logger logger;
    private final ConfigService service;

    public MainConfigHandler(ServerFeatures plugin, ConfigService service) {
        this(plugin.getLogger(), service);
    }

    MainConfigHandler(Logger logger, ConfigService service) {
        super(service.open("config.yml", true), "");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.service = Objects.requireNonNull(service, "service");
        injectGlobalDefaults(Map.of("server_name", "server"));
    }

    public void reloadConfig() {
        file.reload();
    }

    public FeatureConfigHandler openFeatureConfig(String featureName) {
        return new FeatureConfigHandler(
                service,
                globals(),
                FeatureStoragePaths.normalizeFeatureName(featureName),
                logger
        );
    }

    public void registerFeature(String featureName) {
        openFeatureConfig(featureName).putIfAbsent("enabled", false);
    }

    public void injectFeatureDefaults(String featureName, ConfigMap defaults) {
        openFeatureConfig(featureName).injectDefaults(defaults);
    }

    public boolean isFeatureEnabled(String featureName) {
        return openFeatureConfig(featureName).get("enabled", Boolean.class, false);
    }

    public void setFeatureEnabled(String featureName, boolean enabled) {
        openFeatureConfig(featureName).put("enabled", enabled);
    }

    public void migrateLegacyFeatureConfig(String featureName) {
        String legacyKey = resolveLegacyFeatureSectionKey(featureName);
        if (legacyKey == null) {
            return;
        }
        Object raw = get("features." + legacyKey);
        if (raw == null) {
            return;
        }
        openFeatureConfig(featureName).mergeMissingRaw(raw);
        remove("features." + legacyKey);
        logger.info("[ServerFeatures] [Config] Migrated legacy feature section 'features."
                + legacyKey + "' to '" + FeatureStoragePaths.configPath(featureName) + "'");
    }

    public void cleanupLegacyFeatureSections() {
        ConfigNode features = node("features");
        if (!features.isNull() && features.keys().isEmpty()) {
            remove("features");
            logger.info("[ServerFeatures] [Config] Removed empty legacy 'features' section from config.yml");
        }
    }

    /**
     * Compatibility entry point. Per-feature files are intentionally retained when a feature is unavailable.
     */
    public void cleanupUnusedFeatures(Set<String> registeredFeatures) {
        Objects.requireNonNull(registeredFeatures, "registeredFeatures");
        cleanupLegacyFeatureSections();
    }

    public Object getGlobalSetting(String key) {
        return get("global." + key);
    }

    public <T> T getGlobalSetting(String key, Class<T> type) {
        return get("global." + key, type);
    }

    public <T> T getGlobalSetting(String key, Class<T> type, T def) {
        return get("global." + key, type, def);
    }

    public ConfigNode globalNode(String key) {
        return node("global." + key);
    }

    private void injectGlobalDefaults(Map<String, Object> defaults) {
        defaults.forEach((key, value) -> {
            String path = "global." + key;
            if (putIfAbsent(path, value)) {
                logger.info("[ServerFeatures] [Config] Added missing global key '" + path + "'");
            }
        });
    }

    private String resolveLegacyFeatureSectionKey(String featureName) {
        ConfigNode features = node("features");
        if (features.isNull()) {
            return null;
        }
        for (String key : features.keys()) {
            if (key.equals(featureName)) {
                return key;
            }
        }
        for (String key : features.keys()) {
            if (key.equalsIgnoreCase(featureName)) {
                return key;
            }
        }
        return null;
    }
}
