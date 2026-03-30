package nl.hauntedmc.serverfeatures.features;

import nl.hauntedmc.serverfeatures.ServerFeatures;

import java.util.logging.Level;

public class FeatureFactory {

    public static BukkitBaseFeature<?> createFeature(Class<? extends BukkitBaseFeature<?>> featureClass, ServerFeatures plugin) {
        try {
            return featureClass.getDeclaredConstructor(ServerFeatures.class).newInstance(plugin);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Failed to instantiate feature: " + featureClass.getSimpleName(), t);
            return null;
        }
    }
}
