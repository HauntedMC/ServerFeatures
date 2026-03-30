package nl.hauntedmc.serverfeatures.features.nickname.meta;


import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;

import java.util.List;

public class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "Nickname";
    }

    @Override
    public String getFeatureVersion() {
        return "1.0.0";
    }

    @Override
    public List<String> getPluginDependencies() {
        return List.of("DataProvider", "DataRegistry");
    }

}
