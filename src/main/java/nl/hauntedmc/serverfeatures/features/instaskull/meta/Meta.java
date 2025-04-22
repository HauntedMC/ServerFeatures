package nl.hauntedmc.serverfeatures.features.instaskull.meta;


import nl.hauntedmc.commonlib.featureapi.meta.BaseMeta;

import java.util.List;

public class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "InstaSkull";
    }

    @Override
    public String getFeatureVersion() {
        return "1.0.0";
    }

    @Override
    public List<String> getDependencies() {
        return List.of();
    }
}
