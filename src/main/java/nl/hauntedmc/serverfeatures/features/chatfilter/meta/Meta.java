package nl.hauntedmc.serverfeatures.features.chatfilter.meta;

import nl.hauntedmc.serverfeatures.features.BaseMeta;

public class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "ChatFilter";
    }

    @Override
    public String getFeatureVersion() {
        return "1.0.0";
    }
}
