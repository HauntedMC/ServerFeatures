package nl.hauntedmc.serverfeatures.features.autolapis.meta;

import nl.hauntedmc.commonlib.featureapi.feature.meta.BaseMeta;

public class Meta implements BaseMeta {
    @Override
    public String getFeatureName() {
        return "AutoLapis";
    }

    @Override
    public String getFeatureVersion() {
        return "1.0.0";
    }
}