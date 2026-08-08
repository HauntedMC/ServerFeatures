package nl.hauntedmc.serverfeatures.api.feature;

import java.util.Objects;
import java.util.Set;

/** Immutable public metadata for a built-in feature. */
public record FeatureDescriptor(
        FeatureId id,
        String displayName,
        String version,
        FeatureClassification classification,
        Set<FeatureId> requiredFeatures,
        Set<String> providedCapabilities,
        Set<FeatureRole> roles
) {
    public FeatureDescriptor {
        Objects.requireNonNull(id, "id");
        displayName = requireText(displayName, "displayName");
        version = requireText(version, "version");
        classification = Objects.requireNonNull(classification, "classification");
        requiredFeatures = requiredFeatures == null ? Set.of() : Set.copyOf(requiredFeatures);
        providedCapabilities = providedCapabilities == null ? Set.of() : Set.copyOf(providedCapabilities);
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public FeatureDescriptor(FeatureId id, String displayName, String version,
                             FeatureClassification classification, Set<FeatureId> requiredFeatures,
                             Set<String> providedCapabilities) {
        this(id, displayName, version, classification, requiredFeatures, providedCapabilities,
                deriveRoles(classification, requiredFeatures, providedCapabilities));
    }

    private static Set<FeatureRole> deriveRoles(FeatureClassification classification,
                                                Set<FeatureId> requiredFeatures,
                                                Set<String> providedCapabilities) {
        java.util.EnumSet<FeatureRole> derived = java.util.EnumSet.noneOf(FeatureRole.class);
        if (providedCapabilities != null && !providedCapabilities.isEmpty()) {
            derived.add(FeatureRole.CAPABILITY_PROVIDER);
        }
        if (requiredFeatures != null && !requiredFeatures.isEmpty()) {
            derived.add(FeatureRole.CAPABILITY_CONSUMER);
        }
        if (classification == FeatureClassification.EXTENSION_PROVIDER) {
            derived.add(FeatureRole.EXTENSION_PROVIDER);
        }
        return Set.copyOf(derived);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
