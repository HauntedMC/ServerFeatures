package nl.hauntedmc.serverfeatures.features;

import nl.hauntedmc.serverfeatures.ServerFeatures;

public class FeatureFactory {

    public static BaseFeature<?> createFeature(Class<? extends BaseFeature<?>> featureClass, ServerFeatures plugin) {
        try {
            return featureClass.getDeclaredConstructor(ServerFeatures.class).newInstance(plugin);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to instantiate feature: " + featureClass.getSimpleName() + " - " + e.getMessage());
            return null;
        }
    }
}
