package nl.hauntedmc.serverfeatures.features.graveyard.meta;

import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;

import java.util.List;

public final class Meta implements BaseMeta {
    @Override
    public String getFeatureName() {
        return "Graveyard";
    }

    @Override
    public String getFeatureVersion() {
        return "1.1.0";
    }

    @Override
    public List<String> getPluginDependencies() {
        return List.of(PACKET_EVENTS, DATA_PROVIDER, DATA_REGISTRY);
    }
}
