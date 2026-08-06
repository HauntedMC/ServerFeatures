package nl.hauntedmc.serverfeatures.features.economy.meta;

import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;

import java.util.List;

public final class Meta implements BaseMeta {
    @Override
    public String getFeatureName() {
        return "Economy";
    }

    @Override
    public String getFeatureVersion() {
        return "1.0.0";
    }

    @Override
    public List<String> getPluginDependencies() {
        return List.of(DATA_PROVIDER, DATA_REGISTRY);
    }
}
