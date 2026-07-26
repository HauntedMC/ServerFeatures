package nl.hauntedmc.serverfeatures.features.liquidtank.meta;


import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;

import java.util.List;

public class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "LiquidTank";
    }

    @Override
    public String getFeatureVersion() {
        return "1.1.2";
    }

    @Override
    public List<String> getPluginDependencies() {
        return List.of(PACKET_EVENTS);
    }

}
