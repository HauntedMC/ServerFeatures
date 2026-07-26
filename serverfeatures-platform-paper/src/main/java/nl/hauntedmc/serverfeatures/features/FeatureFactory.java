package nl.hauntedmc.serverfeatures.features;

import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;

import java.util.logging.Level;

public class FeatureFactory {

    public static BukkitBaseFeature<?> createFeature(
            String featureClassName,
            FeatureContext<? extends BaseMeta> context
    ) {
        if (featureClassName == null || featureClassName.isBlank()) {
            context.plugin().getLogger().severe("Failed to instantiate feature: missing feature class name.");
            return null;
        }

        try {
            var plugin = context.plugin();
            Class<?> rawClass = Class.forName(featureClassName, true, plugin.getClass().getClassLoader());
            if (!BukkitBaseFeature.class.isAssignableFrom(rawClass)) {
                plugin.getLogger().severe("Feature class does not extend BukkitBaseFeature: " + featureClassName);
                return null;
            }

            @SuppressWarnings("unchecked")
            Class<? extends BukkitBaseFeature<?>> featureClass = (Class<? extends BukkitBaseFeature<?>>) rawClass;
            var constructor = featureClass.getDeclaredConstructor(FeatureContext.class);
            constructor.setAccessible(true);
            return constructor.newInstance(context);
        } catch (ReflectiveOperationException | LinkageError t) {
            context.plugin().getLogger().log(Level.SEVERE, "Failed to instantiate feature class: " + featureClassName, t);
            return null;
        }
    }
}
