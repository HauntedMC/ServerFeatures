package nl.hauntedmc.serverfeatures.framework.feature;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;
import nl.hauntedmc.serverfeatures.features.FeatureContext;
import nl.hauntedmc.serverfeatures.framework.config.FeatureConfigHandler;
import nl.hauntedmc.serverfeatures.framework.config.FeatureStoragePaths;
import nl.hauntedmc.serverfeatures.framework.config.MainConfigHandler;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureLifecycleFactory;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureLifecycleManager;
import nl.hauntedmc.serverfeatures.framework.localization.LocalizationHandler;
import nl.hauntedmc.serverfeatures.framework.log.FeatureLogger;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Owns stable config, localization, and logger scopes while creating a fresh lifecycle per load.
 */
public final class FeatureScopeFactory {

    private final ServerFeatures plugin;
    private final Function<String, FeatureLifecycleManager> lifecycleFactory;
    private final Function<String, FeatureConfigHandler> configFactory;
    private final Function<String, LocalizationHandler> localizationFactory;
    private final Function<String, FeatureLogger> loggerFactory;
    private final ConcurrentHashMap<String, FeatureScope> scopes = new ConcurrentHashMap<>();

    public FeatureScopeFactory(
            ServerFeatures plugin,
            MainConfigHandler mainConfigHandler,
            LocalizationHandler frameworkLocalization,
            FeatureLifecycleFactory lifecycleFactory
    ) {
        this(
                plugin,
                lifecycleFactory::createLifecycleManager,
                mainConfigHandler::openFeatureConfig,
                frameworkLocalization::openFeatureLocalization,
                name -> new FeatureLogger(plugin.getLogger(), name)
        );
    }

    FeatureScopeFactory(
            ServerFeatures plugin,
            Function<String, FeatureLifecycleManager> lifecycleFactory,
            Function<String, FeatureConfigHandler> configFactory,
            Function<String, LocalizationHandler> localizationFactory,
            Function<String, FeatureLogger> loggerFactory
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lifecycleFactory = Objects.requireNonNull(lifecycleFactory, "lifecycleFactory");
        this.configFactory = Objects.requireNonNull(configFactory, "configFactory");
        this.localizationFactory = Objects.requireNonNull(localizationFactory, "localizationFactory");
        this.loggerFactory = Objects.requireNonNull(loggerFactory, "loggerFactory");
    }

    public <T extends BaseMeta> FeatureContext<T> createContext(T meta) {
        Objects.requireNonNull(meta, "meta");
        String featureName = FeatureStoragePaths.normalizeFeatureName(meta.getFeatureName());
        FeatureScope scope = scopes.computeIfAbsent(featureName, this::createScope);
        return new FeatureContext<>(
                plugin,
                meta,
                scope.configHandler(),
                lifecycleFactory.apply(featureName),
                scope.logger(),
                scope.localizationHandler()
        );
    }

    public FeatureConfigHandler config(String featureName) {
        return scope(featureName).configHandler();
    }

    public LocalizationHandler localization(String featureName) {
        return scope(featureName).localizationHandler();
    }

    public FeatureLogger logger(String featureName) {
        return scope(featureName).logger();
    }

    public void clearCachedScopes() {
        scopes.clear();
    }

    private FeatureScope scope(String featureName) {
        String normalized = FeatureStoragePaths.normalizeFeatureName(featureName);
        return scopes.computeIfAbsent(normalized, this::createScope);
    }

    private FeatureScope createScope(String featureName) {
        return new FeatureScope(
                configFactory.apply(featureName),
                localizationFactory.apply(featureName),
                loggerFactory.apply(featureName)
        );
    }

    private record FeatureScope(
            FeatureConfigHandler configHandler,
            LocalizationHandler localizationHandler,
            FeatureLogger logger
    ) {
        private FeatureScope {
            Objects.requireNonNull(configHandler, "configHandler");
            Objects.requireNonNull(localizationHandler, "localizationHandler");
            Objects.requireNonNull(logger, "logger");
        }
    }
}
