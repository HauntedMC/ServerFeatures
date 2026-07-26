package nl.hauntedmc.serverfeatures.framework.loader.enable;

public enum FeatureEnableResult {
    SUCCESS,
    NOT_FOUND,
    ALREADY_LOADED,
    MISSING_PLUGIN_DEPENDENCY,
    MISSING_FEATURE_DEPENDENCY,
    FAILED
}
