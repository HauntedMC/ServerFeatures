package nl.hauntedmc.serverfeatures.features;

import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;
import nl.hauntedmc.serverfeatures.framework.loader.BuiltInFeatures;

/**
 * Transitional construction entry point used by the loader while feature metadata is moved into
 * the manifest. Built-in construction itself is compile-time typed and performs no reflection.
 */
public final class FeatureFactory {

    private FeatureFactory() { }

    public static BukkitBaseFeature<?> createFeature(
            String featureClassName,
            FeatureContext<? extends BaseMeta> context
    ) {
        if (featureClassName == null || featureClassName.isBlank()) {
            context.plugin().getLogger().severe("Failed to instantiate feature: missing feature class name.");
            return null;
        }

        return BuiltInFeatures.findByImplementationClassName(featureClassName)
                .map(definition -> definition.createFeature(context))
                .orElseGet(() -> {
                    context.plugin().getLogger().severe(
                            "Failed to instantiate feature: implementation is not present in the built-in manifest: "
                                    + featureClassName
                    );
                    return null;
                });
    }
}
