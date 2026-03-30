package nl.hauntedmc.serverfeatures.features;

import nl.hauntedmc.serverfeatures.ServerFeatures;

import java.util.logging.Level;

public class FeatureFactory {

    public static BukkitBaseFeature<?> createFeature(String featureClassName, ServerFeatures plugin) {
        if (featureClassName == null || featureClassName.isBlank()) {
            plugin.getLogger().severe("Failed to instantiate feature: missing feature class name.");
            return null;
        }

        try {
            Class<?> rawClass = Class.forName(featureClassName, true, plugin.getClass().getClassLoader());
            if (!BukkitBaseFeature.class.isAssignableFrom(rawClass)) {
                plugin.getLogger().severe("Feature class does not extend BukkitBaseFeature: " + featureClassName);
                return null;
            }

            @SuppressWarnings("unchecked")
            Class<? extends BukkitBaseFeature<?>> featureClass = (Class<? extends BukkitBaseFeature<?>>) rawClass;
            return featureClass.getDeclaredConstructor(ServerFeatures.class).newInstance(plugin);
        } catch (ReflectiveOperationException | LinkageError t) {
            plugin.getLogger().log(Level.SEVERE, "Failed to instantiate feature class: " + featureClassName, t);
            return null;
        }
    }
}
