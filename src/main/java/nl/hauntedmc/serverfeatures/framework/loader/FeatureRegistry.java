package nl.hauntedmc.serverfeatures.framework.loader;

import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;

import java.util.*;

public class FeatureRegistry {
    private final Map<String, BukkitBaseFeature<?>> loadedFeatures = new HashMap<>();
    private final Map<String, Class<? extends BukkitBaseFeature<?>>> availableFeatures = new HashMap<>();

    public void registerAvailableFeature(String featureName, Class<? extends BukkitBaseFeature<?>> featureClass) {
        availableFeatures.put(featureName, featureClass);
    }

    public void registerLoadedFeature(String featureName, BukkitBaseFeature<?> feature) {
        loadedFeatures.put(featureName, feature);
    }

    public void deregisterLoadedFeature(String featureName) {
        loadedFeatures.remove(featureName);
    }

    public BukkitBaseFeature<?> getLoadedFeature(String featureName) {
        return loadedFeatures.get(featureName);
    }

    public Set<String> getLoadedFeatureNames() {
        return loadedFeatures.keySet();
    }

    public boolean isFeatureLoaded(String featureName) {
        return loadedFeatures.containsKey(featureName);
    }

    public Map<String, Class<? extends BukkitBaseFeature<?>>> getAvailableFeatures() {
        return availableFeatures;
    }

    public List<BukkitBaseFeature<?>> getLoadedFeatures() {
        return new ArrayList<>(loadedFeatures.values());
    }

}
