package nl.hauntedmc.serverfeatures.lifecycle;

import nl.hauntedmc.serverfeatures.common.BaseFeature;

import java.util.*;

public class FeatureRegistry {
    private final Map<String, BaseFeature<?>> loadedFeatures = new HashMap<>();
    private final Map<String, Class<? extends BaseFeature<?>>> availableFeatures = new HashMap<>();

    public void registerAvailableFeature(String featureName, Class<? extends BaseFeature<?>> featureClass) {
        availableFeatures.put(featureName, featureClass);
    }

    public void registerLoadedFeature(String featureName, BaseFeature<?> feature) {
        loadedFeatures.put(featureName, feature);
    }

    public void deregisterLoadedFeature(String featureName) {
        loadedFeatures.remove(featureName);
    }

    public BaseFeature<?> getLoadedFeature(String featureName) {
        return loadedFeatures.get(featureName);
    }

    public Set<String> getLoadedFeatureNames() {
        return loadedFeatures.keySet();
    }

    public boolean isFeatureLoaded(String featureName) {
        return loadedFeatures.containsKey(featureName);
    }

    public Map<String, Class<? extends BaseFeature<?>>> getAvailableFeatures() {
        return availableFeatures;
    }

    public List<BaseFeature<?>> getLoadedFeatures() {
        return new ArrayList<>(loadedFeatures.values());
    }

}
