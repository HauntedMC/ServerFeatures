package nl.hauntedmc.serverfeatures.features.spawnertoggle.meta;

import nl.hauntedmc.serverfeatures.features.BaseMeta;

import java.util.List;

public class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "SpawnerToggle";
    }

    @Override
    public String getFeatureVersion() {
        return "1.0.0";
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
