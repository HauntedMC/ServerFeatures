package nl.hauntedmc.serverfeatures.framework.loader;

import nl.hauntedmc.serverfeatures.features.BukkitBaseFeature;

import java.util.*;

public class FeatureRegistry {
    private final Map<String, BukkitBaseFeature<?>> loadedFeatures = new LinkedHashMap<>();
    private final Map<String, FeatureDescriptor> availableFeatures = new LinkedHashMap<>();

    public void registerAvailableFeature(FeatureDescriptor descriptor) {
        availableFeatures.put(descriptor.registryName(), descriptor);
    }

    public void deregisterAvailableFeature(String featureName) {
        availableFeatures.remove(featureName);
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
        return Collections.unmodifiableSet(new LinkedHashSet<>(loadedFeatures.keySet()));
    }

    public boolean isFeatureLoaded(String featureName) {
        return loadedFeatures.containsKey(featureName);
    }

    public Map<String, FeatureDescriptor> getAvailableFeatures() {
        return Collections.unmodifiableMap(availableFeatures);
    }

    public FeatureDescriptor getAvailableFeature(String featureName) {
        return availableFeatures.get(featureName);
    }

    public List<BukkitBaseFeature<?>> getLoadedFeatures() {
        return new ArrayList<>(loadedFeatures.values());
    }

}
