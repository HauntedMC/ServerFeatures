package nl.hauntedmc.serverfeatures.features.playerdata.meta;

import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;

public final class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "PlayerData";
    }

    @Override
    public String getFeatureVersion() {
        return "1.0.0";
    }
}
