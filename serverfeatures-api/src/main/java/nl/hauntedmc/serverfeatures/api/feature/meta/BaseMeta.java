package nl.hauntedmc.serverfeatures.api.feature.meta;

import java.util.List;

public interface BaseMeta {
    String DATA_PROVIDER = "DataProvider";
    String DATA_REGISTRY = "DataRegistry";
    String PACKET_EVENTS = "packetevents";

    String getFeatureName();

    String getFeatureVersion();

    default List<String> getDependencies() {
        return List.of();
    }

    default List<String> getPluginDependencies() {
        return List.of();
    }
}
