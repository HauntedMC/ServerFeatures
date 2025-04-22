package nl.hauntedmc.serverfeatures.config;

import nl.hauntedmc.serverfeatures.ServerFeatures;

public class FeatureConfigHandler extends MainConfigHandler {

    private final String featureName;

    public FeatureConfigHandler(ServerFeatures plugin, String featureName) {
        super(plugin);
        this.featureName = featureName;
    }

    /**
     * Get a feature-specific setting.
     */
    public Object getSetting(String key) {
        return config.get("features." + featureName + "." + key);
    }

}
