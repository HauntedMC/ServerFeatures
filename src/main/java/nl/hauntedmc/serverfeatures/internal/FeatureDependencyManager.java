package nl.hauntedmc.serverfeatures.internal;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureFactory;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class FeatureDependencyManager {

    private final FeatureLoadManager featureLoadManager;
    private final Logger logger;
    private final ServerFeatures plugin;

    public FeatureDependencyManager(FeatureLoadManager featureLoadManager, ServerFeatures plugin) {
        this.featureLoadManager = featureLoadManager;
        this.logger = plugin.getLogger();
        this.plugin = plugin;
    }

    /**
     * Ensures that all dependencies of a feature are enabled before loading.
     */
    public boolean areDependenciesMet(BaseFeature<?> feature) {
        return checkDependencies(feature, new HashSet<>()) && arePluginDependenciesMet(feature);
    }

    /**
     * Recursively checks dependencies and ensures they are enabled.
     */
    private boolean checkDependencies(BaseFeature<?> feature, Set<String> visited) {
        String featureName = feature.getFeatureName();

        // 🔹 Prevent circular dependencies
        if (visited.contains(featureName)) {
            logger.warning("Circular dependency detected: " + featureName);
            return false;
        }

        visited.add(featureName);
        List<String> dependencies = feature.getDependencies();

        for (String dependency : dependencies) {
            if (!featureLoadManager.getFeatureRegistry().isFeatureLoaded(dependency)) {
                logger.info("Enabling dependency " + dependency + " for " + featureName);

                BaseFeature<?> dependencyFeature = FeatureFactory.createFeature(
                        featureLoadManager.getFeatureRegistry().getAvailableFeatures().get(dependency),
                        this.plugin
                );

                if (dependencyFeature == null) {
                    logger.warning("Failed to instantiate dependency " + dependency + " for " + featureName);
                    return false;
                }

                // 🔹 Recursively check dependencies
                if (!checkDependencies(dependencyFeature, visited)) {
                    return false;
                }

                if (!featureLoadManager.loadFeature(dependency)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Checks if all required external plugins are loaded.
     */
    public boolean arePluginDependenciesMet(BaseFeature<?> feature) {
        List<String> requiredPlugins = feature.getPluginDependencies();

        for (String pluginName : requiredPlugins) {
            if (!isPluginLoaded(pluginName)) {
                logger.warning("Cannot enable " + feature.getFeatureName() + " because required plugin " + pluginName + " is missing.");
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if a specific plugin is loaded.
     */
    public boolean isPluginLoaded(String pluginName) {
        Plugin foundPlugin = Bukkit.getPluginManager().getPlugin(pluginName);
        return foundPlugin != null && foundPlugin.isEnabled();
    }

    /**
     * Finds features that depend on a given feature.
     */
    public List<String> getDependentFeatures(String featureName) {
        return featureLoadManager.getFeatureRegistry().getLoadedFeatureNames().stream()
                .filter(name -> featureLoadManager.getFeatureRegistry().getLoadedFeature(name)
                        .getDependencies().contains(featureName))
                .toList();
    }
}
