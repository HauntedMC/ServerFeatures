package nl.hauntedmc.serverfeatures.framework.loader.dependency;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.framework.loader.FeatureLoadManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class FeatureDependencyManager {

    private final FeatureLoadManager featureLoadManager;
    private final Logger logger;

    public FeatureDependencyManager(FeatureLoadManager featureLoadManager, ServerFeatures plugin) {
        this.featureLoadManager = featureLoadManager;
        this.logger = plugin.getLogger();
    }

    /**
     * Ensures that all dependencies of a feature are enabled before loading.
     */
    public boolean areDependenciesMet(String featureName) {
        String featureKey = featureLoadManager.resolveFeatureKey(featureName);
        if (featureKey == null) {
            logger.warning("Feature not found in registry: " + featureName);
            return false;
        }

        return checkDependencies(featureKey, new HashSet<>(), new HashSet<>())
                && arePluginDependenciesMet(featureKey);
    }

    /**
     * Recursively checks dependencies and ensures they are enabled.
     */
    private boolean checkDependencies(String featureName, Set<String> activePath, Set<String> resolved) {
        return FeatureDependencyTraversal.checkDependencies(
                featureName,
                activePath,
                resolved,
                name -> featureLoadManager.getFeatureRegistry().isFeatureLoaded(name),
                name -> featureLoadManager.getFeatureRegistry().getAvailableFeature(name),
                featureLoadManager::resolveFeatureKey,
                featureLoadManager::loadFeature,
                logger::warning,
                logger::info
        );
    }

    /**
     * Checks if all required external plugins are loaded.
     */
    public boolean arePluginDependenciesMet(String featureName) {
        Set<String> missingPlugins = featureLoadManager.getMissingPluginDependencies(featureName);
        if (!missingPlugins.isEmpty()) {
            logger.warning("Cannot enable " + featureName + " because required plugin(s) "
                    + String.join(", ", missingPlugins) + " are missing.");
            return false;
        }
        return true;
    }

    /**
     * Finds features that depend on a given feature.
     */
    public List<String> getDependentFeatures(String featureName) {
        String targetKey = featureLoadManager.resolveFeatureKey(featureName);
        return FeatureDependentResolver.getDependentFeatures(
                targetKey,
                featureLoadManager.getFeatureRegistry().getLoadedFeatureNames(),
                featureLoadManager.getFeatureRegistry()::getAvailableFeature,
                featureLoadManager::resolveFeatureKey
        );
    }
}
