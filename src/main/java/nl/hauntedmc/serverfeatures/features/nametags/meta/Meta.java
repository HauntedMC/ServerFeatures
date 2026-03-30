package nl.hauntedmc.serverfeatures.features.nametags.meta;

import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;

import java.util.List;


public class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "Nametags";
    }

    @Override
    public String getFeatureVersion() {
        return "1.3.0";
    }

    @Override
    public List<String> getPluginDependencies() {
        return List.of("packetevents", "DataProvider", "DataRegistry");
    }

}
