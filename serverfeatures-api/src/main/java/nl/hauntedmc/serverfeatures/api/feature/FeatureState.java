package nl.hauntedmc.serverfeatures.api.feature;

/** Observable lifecycle state of a built-in feature. */
public enum FeatureState {
    DISABLED,
    STARTING,
    ACTIVE,
    STOPPING,
    FAILED
}
