package nl.hauntedmc.serverfeatures.features.autopickup.meta;

import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;

public final class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "AutoPickup";
    }

    @Override
    public String getFeatureVersion() {
        return "1.0.0";
    }
}
