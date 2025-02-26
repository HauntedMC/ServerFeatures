package nl.hauntedmc.serverfeatures.internal.events;

/**
 * Event triggered when a feature is disabled.
 */
public class FeatureDisabledEvent {
    private final String featureName;

    public FeatureDisabledEvent(String featureName) {
        this.featureName = featureName;
    }

    public String getFeatureName() {
        return featureName;
    }
}
