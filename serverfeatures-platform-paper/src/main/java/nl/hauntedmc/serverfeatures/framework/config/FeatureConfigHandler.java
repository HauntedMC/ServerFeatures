package nl.hauntedmc.serverfeatures.framework.config;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigService;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigView;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Feature-level config handler backed by {@code features/<Feature>/config.yml}.
 */
public class FeatureConfigHandler extends ConfigView {

    private final String featureName;
    private final Logger logger;
    private final ConfigView globalView;
    private final List<Runnable> reloadListeners = new CopyOnWriteArrayList<>();

    FeatureConfigHandler(ConfigService service, ConfigView globalView, String featureName, Logger logger) {
        super(service.open(FeatureStoragePaths.configPath(featureName), false), "");
        this.featureName = FeatureStoragePaths.normalizeFeatureName(featureName);
        this.logger = Objects.requireNonNull(logger, "logger");
        this.globalView = Objects.requireNonNull(globalView, "globalView");
    }

    public void reloadConfig() {
        file.reload();
        reloadListeners.forEach(Runnable::run);
    }

    /**
     * Registers a callback that runs after this feature's configuration has been reloaded from disk.
     * Listeners are scoped to this handler instance and are discarded with the owning feature context.
     */
    public void registerReloadListener(Runnable listener) {
        reloadListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public String featureName() {
        return featureName;
    }

    public void injectDefaults(ConfigMap defaults) {
        if (defaults == null) {
            return;
        }
        reconcileMismatchedKeyTypes(defaults);
        ConfigDefaultsMerger.mergeMissingPaths(this, defaults.toMap()).forEach(key ->
                logger.info("[ServerFeatures] [Config] Added missing key '" + key
                        + "' for feature '" + featureName + "'")
        );
    }

    @Override
    public ConfigView globals() {
        return globalView;
    }

    public Object getGlobalSetting(String key) {
        return globals().get(key);
    }

    public <T> T getGlobalSetting(String key, Class<T> type) {
        return globals().get(key, type);
    }

    public <T> T getGlobalSetting(String key, Class<T> type, T def) {
        return globals().get(key, type, def);
    }

    public ConfigNode globalNode(String key) {
        return globals().node(key);
    }

    private void reconcileMismatchedKeyTypes(ConfigMap defaults) {
        ConfigNode section = node();
        for (String topKey : section.keys()) {
            FeatureConfigSchema.Kind expected = FeatureConfigSchema.expectedKindForTopKey(topKey, defaults);
            if (expected == null) {
                continue;
            }
            FeatureConfigSchema.Kind actual = FeatureConfigSchema.classify(node(topKey).raw());
            if (actual != null && expected != actual) {
                remove(topKey);
                logger.info("[ServerFeatures] [Config] Removed key '" + topKey
                        + "' from feature '" + featureName + "' due to schema change");
            }
        }
    }
}
