package nl.hauntedmc.serverfeatures.framework.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * In-process catalog for APIs exported by loaded ServerFeatures features.
 */
public final class FeatureServiceCatalog {

    private final Map<Class<?>, Registration> services = new LinkedHashMap<>();

    public synchronized <T> void register(String ownerFeature, Class<T> type, T service) {
        String owner = requireText(ownerFeature);
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(service, "service");

        Registration existing = services.get(type);
        if (existing != null && !existing.ownerFeature().equals(owner)) {
            throw new IllegalStateException("Feature service type '" + type.getName()
                    + "' is already owned by feature '" + existing.ownerFeature() + "'.");
        }
        services.put(type, new Registration(owner, service));
    }

    public synchronized void unregister(String ownerFeature, Class<?> type, Object service) {
        String owner = requireText(ownerFeature);
        Objects.requireNonNull(type, "type");
        Registration existing = services.get(type);
        if (existing != null
                && existing.ownerFeature().equals(owner)
                && existing.service() == service) {
            services.remove(type);
        }
    }

    public synchronized <T> Optional<T> find(Class<T> type) {
        Objects.requireNonNull(type, "type");
        Registration registration = services.get(type);
        if (registration == null) {
            return Optional.empty();
        }
        return Optional.of(type.cast(registration.service()));
    }

    private static String requireText(String value) {
        Objects.requireNonNull(value, "ownerFeature");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("ownerFeature must not be blank");
        }
        return normalized;
    }

    private record Registration(String ownerFeature, Object service) {
    }
}
