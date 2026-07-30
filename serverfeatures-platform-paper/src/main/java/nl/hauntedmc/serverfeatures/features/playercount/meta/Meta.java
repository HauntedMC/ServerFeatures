package nl.hauntedmc.serverfeatures.features.playercount.meta;

import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;

import java.util.List;

public final class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "PlayerCount";
    }

    @Override
    public String getFeatureVersion() {
        return "1.0.0";
    }

    @Override
    public List<String> getPluginDependencies() {
        return List.of(DATA_PROVIDER);
    }
}
