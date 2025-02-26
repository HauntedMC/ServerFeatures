package nl.hauntedmc.serverfeatures.internal;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.common.BaseFeature;
import nl.hauntedmc.serverfeatures.config.ConfigHandler;
import nl.hauntedmc.serverfeatures.internal.events.FeatureDisabledEvent;
import nl.hauntedmc.serverfeatures.internal.events.FeatureLoadedEvent;
import nl.hauntedmc.serverfeatures.features.FeatureFactory;
import nl.hauntedmc.serverfeatures.localization.LocalizationHandler;

import java.util.*;
import java.util.logging.Level;

public class FeatureLoadManager {

    private final ServerFeatures plugin;
    private final ConfigHandler configHandler;
    private final FeatureRegistry featureRegistry;
    private final FeatureDependencyManager dependencyManager;
    private final LocalizationHandler localizationHandler;

    public FeatureLoadManager(ServerFeatures plugin) {
        this.plugin = plugin;
        this.configHandler = plugin.getConfigHandler();
        this.localizationHandler = plugin.getLocalizationHandler();
        this.featureRegistry = new FeatureRegistry();
        this.dependencyManager = new FeatureDependencyManager(this, plugin);
        discoverFeatures();
    }

    /**
     * Uses ClassGraph to dynamically discover all available features.
     */
    private void discoverFeatures() {
        plugin.getLogger().info("[FeatureScanner] Scanning for features...");
        try (var scanResult = new io.github.classgraph.ClassGraph()
                .enableClassInfo()
                .acceptPackages("nl.hauntedmc.serverfeatures.features")
                .scan()) {
            scanResult.getSubclasses(BaseFeature.class.getName()).forEach(classInfo -> {
                try {
                    Class<?> clazz = Class.forName(classInfo.getName());
                    if (BaseFeature.class.isAssignableFrom(clazz)) {
                        @SuppressWarnings("unchecked")
                        Class<? extends BaseFeature<?>> featureClass = (Class<? extends BaseFeature<?>>) clazz;
                        featureRegistry.registerAvailableFeature(classInfo.getSimpleName(), featureClass);
                    }
                } catch (ClassNotFoundException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to load feature class: " + classInfo.getName(), e);
                }
            });
        }
        plugin.getLogger().info("Discovered features: " + featureRegistry.getAvailableFeatures().keySet());
        configHandler.cleanupUnusedFeatures(featureRegistry.getAvailableFeatures().keySet());
    }

    /**
     * Initializes all enabled features using topological sorting.
     */
    public void initializeFeatures() {
        Set<String> visited = new HashSet<>();
        List<String> loadOrder = new ArrayList<>();

        for (String featureName : featureRegistry.getAvailableFeatures().keySet()) {
            if (!visited.contains(featureName)) {
                if (!resolveFeatureLoadOrder(featureName, new HashSet<>(), visited, loadOrder)) {
                    plugin.getLogger().severe("Dependency cycle detected! Feature loading aborted.");
                    return;
                }
            }
        }

        for (String featureName : loadOrder) {
            loadFeature(featureName);
        }
    }

    /**
     * Recursively determines the correct feature loading order.
     */
    private boolean resolveFeatureLoadOrder(String featureName, Set<String> stack, Set<String> visited, List<String> loadOrder) {
        if (stack.contains(featureName)) return false; // Cycle detected
        if (visited.contains(featureName)) return true;

        stack.add(featureName);
        visited.add(featureName);

        BaseFeature<?> feature = FeatureFactory.createFeature(featureRegistry.getAvailableFeatures().get(featureName), plugin);
        if (feature != null) {
            for (String dependency : feature.getDependencies()) {
                if (!resolveFeatureLoadOrder(dependency, stack, visited, loadOrder)) {
                    return false;
                }
            }
        }

        stack.remove(featureName);
        loadOrder.add(featureName);
        return true;
    }


    /**
     * Enables and loads a feature dynamically.
     */
    public boolean enableFeature(String featureName) {
        if (!featureRegistry.getAvailableFeatures().containsKey(featureName)) {
            plugin.getLogger().warning("Feature not found: " + featureName);
            return false;
        }
        configHandler.setFeatureEnabled(featureName, true);
        return loadFeature(featureName);
    }


    /**
     * Loads and initializes a feature.
     */
    public boolean loadFeature(String featureName) {
        if (featureRegistry.isFeatureLoaded(featureName)) {
            plugin.getLogger().warning("Feature already loaded: " + featureName);
            return false;
        }

        BaseFeature<?> feature = FeatureFactory.createFeature(featureRegistry.getAvailableFeatures().get(featureName), plugin);
        if (feature == null) return false;

        configHandler.registerFeature(featureName);
        configHandler.injectFeatureDefaults(featureName, feature.getDefaultConfig());
        localizationHandler.registerDefaultMessages(feature.getDefaultMessages());

        if (configHandler.isFeatureEnabled(featureName)) {
            if (!dependencyManager.areDependenciesMet(feature)) {
                plugin.getLogger().warning("Feature " + featureName + " is missing dependencies and cannot be enabled.");
                return false;
            }

            feature.initialize();
            featureRegistry.registerLoadedFeature(featureName, feature);
            plugin.getLogger().info("Feature loaded: " + featureName);
            FeatureEventManager.triggerEvent(new FeatureLoadedEvent(featureName));
            return true;
        }

        return false;
    }

    /**
     * Disables and unloads a feature dynamically.
     */
    public boolean disableFeature(String featureName) {
        BaseFeature<?> feature = featureRegistry.getLoadedFeature(featureName);
        if (feature == null) {
            plugin.getLogger().warning("Feature not currently loaded: " + featureName);
            return false;
        }
        feature.cleanup();
        configHandler.setFeatureEnabled(featureName, false);
        plugin.getLogger().info("Feature disabled: " + featureName);
        featureRegistry.deregisterLoadedFeature(featureName);
        FeatureEventManager.triggerEvent(new FeatureDisabledEvent(featureName));

        // Disable dependent features
        for (String dependent : dependencyManager.getDependentFeatures(featureName)) {
            disableFeature(dependent);
        }

        return true;
    }

    /**
     * Reloads a feature dynamically, ensuring dependent features reload afterward.
     */
    public boolean reloadFeature(String featureName) {
        if (!featureRegistry.isFeatureLoaded(featureName)) {
            plugin.getLogger().warning("Feature not currently loaded: " + featureName);
            return false;
        }

        configHandler.reloadConfig();
        localizationHandler.reloadLocalization();
        BaseFeature<?> feature = featureRegistry.getLoadedFeature(featureName);
        feature.cleanup();
        featureRegistry.deregisterLoadedFeature(featureName);

        boolean hasReloaded = loadFeature(featureName);

        if (hasReloaded) {
            plugin.getLogger().info("Feature " + featureName + " reloaded.");

            // Reload dependent features automatically
            for (String dependent : dependencyManager.getDependentFeatures(featureName)) {
                plugin.getLogger().info("Reloading dependent feature: " + dependent);
                reloadFeature(dependent);
            }
        }

        return hasReloaded;
    }

    /**
     * Returns the feature registry for tracking features.
     */
    public FeatureRegistry getFeatureRegistry() {
        return featureRegistry;
    }

    /**
     * Unload all currently loaded features.
     */
    public void unloadAllFeatures() {
        plugin.getLogger().info("Unloading all loaded features...");

        List<BaseFeature<?>> loadedFeatures = featureRegistry.getLoadedFeatures();

        for (BaseFeature<?> feature : loadedFeatures) {
            feature.cleanup();
        }

        plugin.getLogger().info("All features have been unloaded.");
    }

}
