package nl.hauntedmc.serverfeatures.api;

import java.util.Objects;

/** Semantic API and implementation versions exposed by a running ServerFeatures platform. */
public record ServerFeaturesApiVersion(String apiVersion, String implementationVersion) {
    public static final String CURRENT = "3.3.0";

    public ServerFeaturesApiVersion {
        apiVersion = requireVersion(apiVersion, "apiVersion");
        implementationVersion = requireVersion(implementationVersion, "implementationVersion");
    }

    /** Creates a version pair for the current API. */
    public static ServerFeaturesApiVersion current(String implementationVersion) {
        return new ServerFeaturesApiVersion(CURRENT, implementationVersion);
    }

    private static String requireVersion(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
