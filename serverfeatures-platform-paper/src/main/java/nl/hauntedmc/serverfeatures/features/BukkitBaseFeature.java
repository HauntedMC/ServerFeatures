package nl.hauntedmc.serverfeatures.features;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.feature.Feature;
import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.serverfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.serverfeatures.framework.config.FeatureConfigHandler;
import nl.hauntedmc.serverfeatures.framework.lifecycle.FeatureLifecycleManager;
import nl.hauntedmc.serverfeatures.framework.localization.LocalizationHandler;
import nl.hauntedmc.serverfeatures.framework.log.FeatureLogger;

import java.util.List;

public abstract class BukkitBaseFeature<T extends BaseMeta> implements Feature {

    private final FeatureContext<T> context;

    protected BukkitBaseFeature(FeatureContext<T> context) {
        this.context = java.util.Objects.requireNonNull(context, "context");
    }

    public String getFeatureName() {
        return context.meta().getFeatureName();
    }

    public String getFeatureVersion() {
        return context.meta().getFeatureVersion();
    }

    public List<String> getDependencies() {
        return context.meta().getDependencies();
    }

    public List<String> getPluginDependencies() {
        return context.meta().getPluginDependencies();
    }

    public FeatureContext<T> getContext() {
        return context;
    }

    public FeatureLogger getLogger() {
        return context.logger();
    }

    public ServerFeatures getPlugin() {
        return context.plugin();
    }

    public FeatureConfigHandler getConfigHandler() {
        return context.configHandler();
    }

    public FeatureLifecycleManager getLifecycleManager() {
        return context.lifecycleManager();
    }

    public LocalizationHandler getLocalizationHandler() {
        return context.localizationHandler();
    }

    /**
     * Each feature should define its default settings.
     */
    public abstract ConfigMap getDefaultConfig();

    /**
     * Each feature should define its default messages.
     */
    public abstract MessageMap getDefaultMessages();


    /**
     * Feature initialization logic (must be implemented by each feature).
     */
    public abstract void initialize();

    /**
     * Feature disable logic (must be implemented by each feature).
     */
    public abstract void disable();

    /**
     * Properly unloads the feature using the lifecycle manager.
     */
    public void cleanup() {
        getPlugin().getLogger().info("Disabling " + getFeatureName());
        Throwable failure = null;
        try {
            disable();
        } catch (Throwable throwable) {
            failure = throwable;
        }
        try {
            getLifecycleManager().cleanup();
        } catch (Throwable throwable) {
            if (failure == null) {
                failure = throwable;
            } else {
                failure.addSuppressed(throwable);
            }
        }
        if (failure != null) {
            throwUnchecked(failure);
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwUnchecked(Throwable throwable) throws E {
        throw (E) throwable;
    }
}
