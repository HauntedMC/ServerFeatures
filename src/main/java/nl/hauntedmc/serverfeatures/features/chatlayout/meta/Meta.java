package nl.hauntedmc.serverfeatures.features.chatlayout.meta;


import nl.hauntedmc.commonlib.featureapi.feature.meta.BaseMeta;

import java.util.List;

public class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "ChatLayout";
    }

    @Override
    public String getFeatureVersion() {
        return "1.0.0";
    }

    @Override
    public List<String> getPluginDependencies() {
        return List.of("PlaceholderAPI");
    }
}
