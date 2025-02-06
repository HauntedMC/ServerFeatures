package nl.hauntedmc.serverfeatures.events.registry;

/**
 * Event triggered when a feature is loaded.
 */
public class FeatureLoadedEvent {
    private final String featureName;

    public FeatureLoadedEvent(String featureName) {
        this.featureName = featureName;
    }

    public String getFeatureName() {
        return featureName;
    }
}
