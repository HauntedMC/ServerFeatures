package nl.hauntedmc.serverfeatures.features;

import nl.hauntedmc.commonlib.featureapi.Feature;
import nl.hauntedmc.commonlib.featureapi.meta.BaseMeta;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.lifecycle.FeatureLifecycleManager;
import nl.hauntedmc.serverfeatures.internal.FeatureConfigHandler;
import nl.hauntedmc.serverfeatures.localization.LocalizationHandler;

import java.util.List;
import java.util.Map;

public abstract class BukkitBaseFeature<T extends BaseMeta> implements Feature {

    private final ServerFeatures plugin;
    private final T meta;
    private final FeatureConfigHandler configHandler;
    private final FeatureLifecycleManager lifecycleManager;

    protected BukkitBaseFeature(ServerFeatures plugin, T meta) {
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
        plugin.getLogger().info("Disabling " + getFeatureName());
        disable();
        lifecycleManager.cleanup();
    }
}
