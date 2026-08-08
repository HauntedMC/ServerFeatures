package nl.hauntedmc.serverfeatures.framework.lifecycle;

/** Lifecycle state shared by feature-scoped resource managers. */
public enum FeatureResourceState {
    OPEN,
    QUIESCING,
    CLOSED
}
