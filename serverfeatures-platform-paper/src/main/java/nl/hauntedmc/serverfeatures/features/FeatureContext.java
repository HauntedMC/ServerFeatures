package nl.hauntedmc.serverfeatures.features;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;
import nl.hauntedmc.serverfeatures.framework.config.FeatureConfigHandler;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureLifecycleManager;
import nl.hauntedmc.serverfeatures.framework.localization.LocalizationHandler;
import nl.hauntedmc.serverfeatures.framework.log.FeatureLogger;

import java.util.Objects;

/**
 * Immutable bundle of the framework-owned objects for one feature instance.
 */
public final class FeatureContext<T extends BaseMeta> {

    private final ServerFeatures plugin;
    private final T meta;
    private final FeatureConfigHandler configHandler;
    private final FeatureLifecycleManager lifecycleManager;
    private final FeatureLogger logger;
    private final LocalizationHandler localizationHandler;

    public FeatureContext(
            ServerFeatures plugin,
            T meta,
            FeatureConfigHandler configHandler,
            FeatureLifecycleManager lifecycleManager,
            FeatureLogger logger,
            LocalizationHandler localizationHandler
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.meta = Objects.requireNonNull(meta, "meta");
        this.configHandler = Objects.requireNonNull(configHandler, "configHandler");
        this.lifecycleManager = Objects.requireNonNull(lifecycleManager, "lifecycleManager");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.localizationHandler = Objects.requireNonNull(localizationHandler, "localizationHandler");
    }

    public ServerFeatures plugin() {
        return plugin;
    }

    public T meta() {
        return meta;
    }

    public FeatureConfigHandler configHandler() {
        return configHandler;
    }

    public FeatureLifecycleManager lifecycleManager() {
        return lifecycleManager;
    }

    public FeatureLogger logger() {
        return logger;
    }

    public LocalizationHandler localizationHandler() {
        return localizationHandler;
    }
}
