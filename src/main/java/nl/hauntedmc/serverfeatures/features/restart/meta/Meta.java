package nl.hauntedmc.serverfeatures.features.restart.meta;


import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;

public class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "Restart";
    }

    @Override
    public String getFeatureVersion() {
        return "1.0.0";
    }
}
