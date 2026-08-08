package nl.hauntedmc.serverfeatures.api;

/** Lifecycle state of the ServerFeatures runtime as a whole. */
public enum RuntimeState {
    STARTING,
    READY,
    RELOADING,
    DEGRADED,
    STOPPING,
    STOPPED
}
