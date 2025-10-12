package nl.hauntedmc.serverfeatures.features.staffchat.meta;


import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;

public class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "StaffChat";
    }

    @Override
    public String getFeatureVersion() {
        return "1.0.0";
    }
}
