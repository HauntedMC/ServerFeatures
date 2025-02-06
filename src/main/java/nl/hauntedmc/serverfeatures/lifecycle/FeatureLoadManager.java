package nl.hauntedmc.serverfeatures.lifecycle;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.common.BaseFeature;
import nl.hauntedmc.serverfeatures.config.ConfigHandler;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.logging.Level;

public class FeatureLoadManager {

    private final ServerFeatures plugin;
    private final ConfigHandler configHandler;
    private final Map<String, BaseFeature<?>> loadedFeatures = new HashMap<>();
    private final Map<String, Class<? extends BaseFeature<?>>> availableFeatures = new HashMap<>();
    private final FeatureDependencyManager dependencyManager;

    public FeatureLoadManager(ServerFeatures plugin, ConfigHandler configHandler) {
        this.plugin = plugin;
        this.configHandler = configHandler;
        this.dependencyManager = new FeatureDependencyManager(this, plugin);
        discoverFeatures();
    }

    /**
     * Uses ClassGraph to dynamically discover all available features.
     */
    private void discoverFeatures() {
        try (ScanResult scanResult = new ClassGraph()
                .enableClassInfo()
                .acceptPackages("nl.hauntedmc.serverfeatures.features")
                .scan()) {
            scanResult.getSubclasses(BaseFeature.class.getName()).forEach(classInfo -> {
                try {
                    Class<?> clazz = Class.forName(classInfo.getName());
                    if (BaseFeature.class.isAssignableFrom(clazz)) {
                        @SuppressWarnings("unchecked")
                        Class<? extends BaseFeature<?>> featureClass = (Class<? extends BaseFeature<?>>) clazz;
                        availableFeatures.put(classInfo.getSimpleName(), featureClass);
                    }
                } catch (ClassNotFoundException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to load feature class: " + classInfo.getName(), e);
                }
            });
        }
        plugin.getLogger().info("Discovered features: " + availableFeatures.keySet());
        configHandler.cleanupUnusedFeatures(availableFeatures.keySet());
    }

    /**
     * Initializes all enabled features on startup.
     */
    public void initializeAllFeatures() {
        availableFeatures.keySet().forEach(this::loadFeature);
    }

    /**
     * Checks if a feature is currently enabled.
     */
    public boolean isFeatureEnabled(String featureName) {
        return loadedFeatures.containsKey(featureName);
    }


    /**
     * Enables and loads a feature dynamically.
     */
    public boolean enableFeature(String featureName) {
        if (!validateFeatureAvailability(featureName)) return false;

        configHandler.setFeatureEnabled(featureName, true);
        return loadFeature(featureName);
    }

    /**
     * Disables and unloads a feature dynamically.
     */
    public boolean disableFeature(String featureName) {
        BaseFeature<?> feature = loadedFeatures.remove(featureName);
        if (feature == null) {
            plugin.getLogger().warning("Feature not currently loaded: " + featureName);
            return false;
        }

        feature.unload();
        configHandler.setFeatureEnabled(featureName, false);
        plugin.getLogger().info("Feature disabled: " + featureName);
        return true;
    }

    /**
     * Loads and initializes a feature.
     */
    private boolean loadFeature(String featureName) {
        if (loadedFeatures.containsKey(featureName)) {
            plugin.getLogger().warning("Feature already loaded: " + featureName);
            return false;
        }

        BaseFeature<?> feature = instantiateFeature(featureName);
        if (feature == null) return false;

        if (!dependencyManager.areDependenciesMet(feature)) {
            plugin.getLogger().warning("Feature " + featureName + " is missing dependencies and cannot be enabled.");
            return false;
        }

        configHandler.registerFeature(featureName);
        configHandler.injectFeatureDefaults(featureName, feature.getDefaultConfig());

        if (configHandler.isFeatureEnabled(featureName)) {
            loadedFeatures.put(featureName, feature);
            feature.initialize();
            plugin.getLogger().info("Feature loaded: " + featureName);
            return true;
        }

        return false;
    }

    /**
     * Instantiates a feature dynamically.
     */
    private BaseFeature<?> instantiateFeature(String featureName) {
        Class<? extends BaseFeature<?>> featureClass = availableFeatures.get(featureName);
        if (featureClass == null) {
            plugin.getLogger().warning("Feature class not found: " + featureName);
            return null;
        }

        try {
            return featureClass.getDeclaredConstructor(JavaPlugin.class).newInstance(plugin);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to instantiate feature: " + featureName, e);
            return null;
        }
    }

    /**
     * Disables all loaded features.
     */
    public void disableAllLoadedFeatures() {
        loadedFeatures.values().forEach(BaseFeature::unload);
        loadedFeatures.clear();
    }

    /**
     * Reloads all loaded features, ensuring disabled features are removed and new ones are initialized.
     */
    public void reloadAllLoadedFeatures() {
        plugin.getLogger().info("Reloading all features...");
        configHandler.reloadConfig();

        List<String> featuresToReload = new ArrayList<>();

        // Unload features that are now disabled or need reloading
        Iterator<Map.Entry<String, BaseFeature<?>>> iterator = loadedFeatures.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, BaseFeature<?>> entry = iterator.next();
            String featureName = entry.getKey();

            if (!configHandler.isFeatureEnabled(featureName)) {
                entry.getValue().unload();
                iterator.remove();
                plugin.getLogger().info("Unloaded feature: " + featureName);
            } else {
                // Collect for reloading AFTER iteration
                entry.getValue().unload();
                iterator.remove();
                featuresToReload.add(featureName);
            }
        }

        // Reload collected features AFTER iteration
        for (String featureName : featuresToReload) {
            loadFeature(featureName);
            plugin.getLogger().info("Reloaded feature: " + featureName);
        }

        // Load newly enabled features
        availableFeatures.keySet().stream()
                .filter(featureName -> configHandler.isFeatureEnabled(featureName) && !loadedFeatures.containsKey(featureName))
                .forEach(this::loadFeature);

        plugin.getLogger().info("All features reloaded.");
    }


    /**
     * Reloads a single feature dynamically.
     */
    public boolean reloadFeature(String featureName) {
        BaseFeature<?> feature = loadedFeatures.get(featureName);
        if (feature == null) {
            plugin.getLogger().warning("Feature not currently loaded: " + featureName);
            return false;
        }

        configHandler.reloadConfig();
        feature.unload();
        loadedFeatures.remove(featureName);
        return loadFeature(featureName);
    }

    /**
     * Returns the list of currently loaded features.
     */
    public List<BaseFeature<?>> getLoadedFeatures() {
        return new ArrayList<>(loadedFeatures.values());
    }

    /**
     * Validates if a feature exists and is available.
     */
    private boolean validateFeatureAvailability(String featureName) {
        if (!availableFeatures.containsKey(featureName)) {
            plugin.getLogger().warning("Feature not found: " + featureName);
            return false;
        }

        if (configHandler.isFeatureEnabled(featureName)) {
            plugin.getLogger().warning("Feature already enabled: " + featureName);
            return false;
        }
        return true;
    }

    public Map<String, Class<? extends BaseFeature<?>>> getAvailableFeatures() {
        return availableFeatures;
    }
}
