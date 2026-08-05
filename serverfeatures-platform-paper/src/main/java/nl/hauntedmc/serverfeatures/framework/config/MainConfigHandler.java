package nl.hauntedmc.serverfeatures.framework.config;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigService;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigView;

import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Global config handler for {@code config.yml} and entry point for feature-scoped configs.
 */
public final class MainConfigHandler extends ConfigView {

    private static final String OVERWRITE_COMMAND_CONFLICTS = "commands.overwrite-conflicts";

    private final Logger logger;
    private final ConfigService service;

    public MainConfigHandler(ServerFeatures plugin, ConfigService service) {
        this(plugin.getLogger(), service);
    }

    MainConfigHandler(Logger logger, ConfigService service) {
        super(service.open("config.yml", true), "");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.service = Objects.requireNonNull(service, "service");
        injectGlobalDefaults(Map.of(
                "server_name", "server",
                OVERWRITE_COMMAND_CONFLICTS, true
        ));
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

    public boolean isFeatureEnabled(String featureName) {
        return openFeatureConfig(featureName).get("enabled", Boolean.class, false);
    }

    public void setFeatureEnabled(String featureName, boolean enabled) {
        openFeatureConfig(featureName).put("enabled", enabled);
    }

    public boolean shouldOverwriteCommandConflicts() {
        return getGlobalSetting(OVERWRITE_COMMAND_CONFLICTS, Boolean.class, true);
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
}
