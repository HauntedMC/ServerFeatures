package nl.hauntedmc.serverfeatures.framework.loader.dependency;

import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.framework.loader.FeatureDescriptor;
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
        return checkDependencies(featureName, new HashSet<>(), new HashSet<>())
                && arePluginDependenciesMet(featureName);
    }

    /**
     * Recursively checks dependencies and ensures they are enabled.
     */
    private boolean checkDependencies(String featureName, Set<String> activePath, Set<String> resolved) {
        if (featureLoadManager.getFeatureRegistry().isFeatureLoaded(featureName)) {
            resolved.add(featureName);
            return true;
        }
        if (resolved.contains(featureName)) {
            return true;
        }

        if (!activePath.add(featureName)) {
            logger.warning("Circular dependency detected: " + featureName);
            return false;
        }

        try {
            FeatureDescriptor descriptor = featureLoadManager.getFeatureRegistry().getAvailableFeature(featureName);
            if (descriptor == null) {
                logger.warning("Feature not found in registry: " + featureName);
                return false;
            }

            for (String dependency : descriptor.featureDependencies()) {
                if (!checkDependencies(dependency, activePath, resolved)) {
                    return false;
                }

                if (!featureLoadManager.getFeatureRegistry().isFeatureLoaded(dependency)) {
                    logger.info("Enabling dependency " + dependency + " for " + featureName);
                    if (!featureLoadManager.loadFeature(dependency)) {
                        return false;
                    }
                }
            }

            resolved.add(featureName);
            return true;
        } finally {
            activePath.remove(featureName);
        }
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
        return featureLoadManager.getFeatureRegistry().getLoadedFeatureNames().stream()
                .filter(name -> {
                    FeatureDescriptor descriptor = featureLoadManager.getFeatureRegistry().getAvailableFeature(name);
                    return descriptor != null && descriptor.featureDependencies().contains(featureName);
                })
                .toList();
    }
}
