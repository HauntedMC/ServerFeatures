package nl.hauntedmc.serverfeatures.features.demofeat.meta;

import nl.hauntedmc.serverfeatures.common.BaseMeta;

import java.util.List;

public class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "DemoFeature";
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
