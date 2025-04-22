package nl.hauntedmc.serverfeatures.features;

import nl.hauntedmc.serverfeatures.ServerFeatures;

public class FeatureFactory {

    public static BukkitBaseFeature<?> createFeature(Class<? extends BukkitBaseFeature<?>> featureClass, ServerFeatures plugin) {
        try {
            return featureClass.getDeclaredConstructor(ServerFeatures.class).newInstance(plugin);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to instantiate feature: " + featureClass.getSimpleName() + " - " + e.getMessage());
            return null;
        }
    }
}
