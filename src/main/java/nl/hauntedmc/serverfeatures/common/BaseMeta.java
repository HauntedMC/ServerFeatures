package nl.hauntedmc.serverfeatures.common;

import java.util.List;

public interface BaseMeta {
    String getFeatureName();
    String getFeatureVersion();

    default List<String> getDependencies() {
        return List.of();
    }

    default List<String> getPluginDependencies() {
        return List.of();
    }
}
