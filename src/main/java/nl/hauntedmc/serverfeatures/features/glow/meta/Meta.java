package nl.hauntedmc.serverfeatures.features.glow.meta;


import nl.hauntedmc.commonlib.featureapi.feature.meta.BaseMeta;

import java.util.List;


public class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "Glow";
    }

    @Override
    public String getFeatureVersion() {
        return "1.3.0";
    }

    @Override
    public List<String> getDependencies() { return List.of("Nametags"); }
}
