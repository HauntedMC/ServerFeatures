package nl.hauntedmc.serverfeatures.framework.config;

import nl.hauntedmc.serverfeatures.api.io.localization.Language;

import java.util.Objects;
import java.util.regex.Pattern;

public final class FeatureStoragePaths {

    private static final Pattern VALID_FEATURE_NAME = Pattern.compile("[A-Za-z0-9_-]+");

    private FeatureStoragePaths() {
    }

    public static String featureDirectory(String featureName) {
        return "features/" + normalizeFeatureName(featureName);
    }

    public static String configPath(String featureName) {
        return featureDirectory(featureName) + "/config.yml";
    }

    public static String messagesPath(String featureName) {
        return featureDirectory(featureName) + "/messages.yml";
    }

    public static String messagesPath(String featureName, Language language) {
        Objects.requireNonNull(language, "language");
        return featureDirectory(featureName) + "/" + language.getFileName();
    }

    public static String normalizeFeatureName(String featureName) {
        String normalized = Objects.requireNonNull(featureName, "featureName").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Feature name cannot be blank");
        }
        if (!VALID_FEATURE_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid feature name for storage path: " + featureName);
        }
        return normalized;
    }
}
