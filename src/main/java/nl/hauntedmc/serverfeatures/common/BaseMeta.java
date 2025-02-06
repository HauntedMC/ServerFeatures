package nl.hauntedmc.serverfeatures.common;

import java.util.List;

public interface BaseMeta {
    String getFeatureName();
    String getFeatureVersion();
    /**
     * Features can override this to declare dependencies.
     */
    default List<String> getDependencies() {
        return List.of(); // Default: no dependencies
    }
}
