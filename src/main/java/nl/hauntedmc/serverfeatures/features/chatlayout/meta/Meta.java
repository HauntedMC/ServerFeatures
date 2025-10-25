package nl.hauntedmc.serverfeatures.features.chatlayout.meta;


import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;

public class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "ChatLayout";
    }

    @Override
    public String getFeatureVersion() {
        return "1.5.0";
    }
}
