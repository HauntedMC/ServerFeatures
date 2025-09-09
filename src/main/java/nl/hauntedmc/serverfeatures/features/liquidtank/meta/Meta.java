package nl.hauntedmc.serverfeatures.features.liquidtank.meta;


import nl.hauntedmc.commonlib.featureapi.feature.meta.BaseMeta;

import java.util.List;

public class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "LiquidTank";
    }

    @Override
    public String getFeatureVersion() {
        return "1.0.0";
    }

    @Override
    public List<String> getPluginDependencies() {
        return List.of("packetevents");
    }

}
