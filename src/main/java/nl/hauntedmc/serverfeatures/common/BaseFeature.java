package nl.hauntedmc.serverfeatures.common;

import nl.hauntedmc.serverfeatures.lifecycle.FeatureLifecycleManager;
import nl.hauntedmc.serverfeatures.config.FeatureConfigHandler;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public abstract class BaseFeature<T extends BaseMeta> {

    protected final JavaPlugin plugin;
    private final T meta;
    protected final FeatureConfigHandler configHandler;
    protected final FeatureLifecycleManager lifecycleManager;

    protected BaseFeature(JavaPlugin plugin, T meta) {
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

    public T getMeta() {
        return meta;
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public FeatureConfigHandler getConfigHandler() {
        return configHandler;
    }

    public FeatureLifecycleManager getLifecycleManager() {
        return lifecycleManager;
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
        lifecycleManager.cleanup();
    }
}
