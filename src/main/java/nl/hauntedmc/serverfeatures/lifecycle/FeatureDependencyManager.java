package nl.hauntedmc.serverfeatures.lifecycle;

import nl.hauntedmc.serverfeatures.common.BaseFeature;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class FeatureDependencyManager {

    private final FeatureLoadManager featureLoadManager;
    private final Logger logger;

    public FeatureDependencyManager(FeatureLoadManager featureLoadManager, JavaPlugin plugin) {
        this.featureLoadManager = featureLoadManager;
        this.logger = plugin.getLogger();
    }

    /**
     * Ensures that all dependencies of a feature are enabled before loading.
     */
    public boolean areDependenciesMet(BaseFeature<?> feature) {
        List<String> dependencies = feature.getDependencies();

        if (dependencies.isEmpty()) return true; // No dependencies

        for (String dependency : dependencies) {
            if (!featureLoadManager.isFeatureEnabled(dependency)) {
                logger.warning("Cannot enable " + feature.getFeatureName() + " because dependency " + dependency + " is not enabled.");
                return false;
            }
        }
        return true;
    }

    /**
     * Finds features that depend on a given feature.
     */
    public List<String> getDependentFeatures(String featureName) {
        List<String> dependents = new ArrayList<>();
        for (BaseFeature<?> feature : this.featureLoadManager.getLoadedFeatures()) {
            if (feature.getDependencies().contains(featureName)) {
                dependents.add(feature.getFeatureName());
            }
        }
        return dependents;
    }
}
