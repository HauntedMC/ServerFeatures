package nl.hauntedmc.serverfeatures.features.afk.meta;

import nl.hauntedmc.commonlib.featureapi.feature.meta.BaseMeta;

public class Meta implements BaseMeta {
    @Override
    public String getFeatureName() {
        return "AFK";
    }

    @Override
    public String getFeatureVersion() {
        return "1.2.0";
    }
}