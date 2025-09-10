package nl.hauntedmc.serverfeatures.features.whitelist.meta;

import nl.hauntedmc.commonlib.featureapi.feature.meta.BaseMeta;

public class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "Whitelist";
    }

    @Override
    public String getFeatureVersion() {
        return "1.1.0";
    }
}
