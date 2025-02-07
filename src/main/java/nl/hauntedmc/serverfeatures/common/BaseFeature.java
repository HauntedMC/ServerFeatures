package nl.hauntedmc.serverfeatures.common;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.lifecycle.FeatureLifecycleManager;
import nl.hauntedmc.serverfeatures.config.FeatureConfigHandler;
import nl.hauntedmc.serverfeatures.localization.LocalizationHandler;

import java.util.List;
import java.util.Map;

public abstract class BaseFeature<T extends BaseMeta> {

    private final ServerFeatures plugin;
    private final T meta;
    private final FeatureConfigHandler configHandler;
    private final FeatureLifecycleManager lifecycleManager;

    protected BaseFeature(ServerFeatures plugin, T meta) {
        this.plugin = plugin;
        this.meta = meta;
        this.configHandler = new FeatureConfigHandler(plugin, getFeatureName());
        this.lifecycleManager = new FeatureLifecycleManager(plugin);
    }

    public String getFeatureName() {
        return meta.getFeatureName();
    }

    public String getFeatureVersion() {
        return meta.getFeatureVersion();
    }

    public List<String> getDependencies() {
        return meta.getDependencies();
    }

    public List<String> getPluginDependencies() {
        return meta.getPluginDependencies();
    }

    public ServerFeatures getPlugin() {
        return plugin;
    }

    public FeatureConfigHandler getConfigHandler() {
        return configHandler;
    }

    public FeatureLifecycleManager getLifecycleManager() {
        return lifecycleManager;
    }

    public LocalizationHandler getLocalizationHandler() {
        return plugin.getLocalizationHandler();
    }

    /**
     * Each feature should define its default settings.
     */
    public abstract Map<String, Object> getDefaultConfig();

    /**
     * Feature initialization logic (must be implemented by each feature).
     */
    public abstract void initialize();

    /**
     * Properly unloads the feature using the lifecycle manager.
     */
    public void unload() {
        plugin.getLogger().info("Unloading feature: " + getFeatureName());
        lifecycleManager.cleanup();
    }
}
