package nl.hauntedmc.serverfeatures.framework.loader.dependency;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;
import nl.hauntedmc.serverfeatures.features.FeatureFactory;
import nl.hauntedmc.serverfeatures.framework.loader.FeatureLoadManager;

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
    public boolean areDependenciesMet(BukkitBaseFeature<?> feature) {
        return checkDependencies(feature, new HashSet<>()) && arePluginDependenciesMet(feature);
    }

    /**
     * Recursively checks dependencies and ensures they are enabled.
     */
    private boolean checkDependencies(BukkitBaseFeature<?> feature, Set<String> visited) {
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

                BukkitBaseFeature<?> dependencyFeature = instantiateFeature(dependency);

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
    public boolean arePluginDependenciesMet(BukkitBaseFeature<?> feature) {
        Set<String> missingPlugins = featureLoadManager.getMissingPluginDependencies(feature);
        if (!missingPlugins.isEmpty()) {
            logger.warning("Cannot enable " + feature.getFeatureName() + " because required plugin(s) "
                    + String.join(", ", missingPlugins) + " are missing.");
            return false;
        }

        return true;
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

    private BukkitBaseFeature<?> instantiateFeature(String featureName) {
        Class<? extends BukkitBaseFeature<?>> featureClass = featureLoadManager
                .getFeatureRegistry()
                .getAvailableFeatures()
                .get(featureName);
        if (featureClass == null) {
            logger.warning("Dependency feature not found: " + featureName);
            return null;
        }
        return FeatureFactory.createFeature(featureClass, this.plugin);
    }
}
