package nl.hauntedmc.serverfeatures.features.combattag.meta;

import nl.hauntedmc.serverfeatures.api.feature.meta.BaseMeta;

import java.util.List;

public final class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "CombatTag";
    }

    @Override
    public String getFeatureVersion() {
        return "1.2.0";
    }

    @Override
    public List<String> getDependencies() {
        return List.of();
    }

    @Override
    public List<String> getPluginDependencies() {
        return List.of();
    }
}
