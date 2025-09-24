package nl.hauntedmc.serverfeatures.api;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Very small service locator for feature APIs.
 * Features register their API instance on initialize() and unregister on disable().
 */
public final class APIRegistry {

    private static final ConcurrentHashMap<Class<?>, Object> SERVICES = new ConcurrentHashMap<>();

    private APIRegistry() {}

    public static <T> void register(Class<T> type, T instance) {
        if (type == null || instance == null) {
            throw new IllegalArgumentException("ApiRegistry.register: type and instance must be non-null");
        }
        SERVICES.put(type, instance);
    }

    public static <T> Optional<T> get(Class<T> type) {
        Object obj = SERVICES.get(type);
        return obj == null ? Optional.empty() : Optional.of(type.cast(obj));
    }

    public static void unregister(Class<?> type) {
        SERVICES.remove(type);
    }

    public static void clear() {
        SERVICES.clear();
    }
}
