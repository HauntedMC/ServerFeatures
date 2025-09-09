package nl.hauntedmc.serverfeatures.internal.action.enable;

public enum FeatureEnableResult {
    SUCCESS,
    NOT_FOUND,
    ALREADY_LOADED,
    MISSING_PLUGIN_DEPENDENCY,
    MISSING_FEATURE_DEPENDENCY,
    FAILED
}
