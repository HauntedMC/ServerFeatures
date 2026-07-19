package nl.hauntedmc.serverfeatures.framework.lifecycle;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.service.FeatureServiceHandle;
import nl.hauntedmc.serverfeatures.api.APIRegistry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Tracks feature-owned API/service registrations and detaches them on cleanup.
 */
public class FeatureApiManager {

    private final Map<Class<?>, Object> registeredServices = new LinkedHashMap<>();
    private final Map<Class<?>, FeatureServiceHandle> dataRegistryServices = new LinkedHashMap<>();
    private final String ownerFeature;
    private final Supplier<Optional<DataRegistry>> dataRegistrySupplier;

    public FeatureApiManager(String ownerFeature, Supplier<Optional<DataRegistry>> dataRegistrySupplier) {
        this.ownerFeature = requireText(ownerFeature, "ownerFeature");
        this.dataRegistrySupplier = Objects.requireNonNull(dataRegistrySupplier, "dataRegistrySupplier");
    }

    /**
     * Registers or replaces a feature-owned API for local and DataRegistry-backed discovery.
     */
    public synchronized <T> void registerService(Class<T> type, T instance) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(instance, "instance");

        Object previous = registeredServices.put(type, instance);
        if (previous != null) {
            if (previous != instance) {
                APIRegistry.unregister(type, previous);
            }
            closeDataRegistryHandle(type);
        }

        APIRegistry.register(type, instance);
        dataRegistrySupplier.get().ifPresent(dataRegistry ->
                dataRegistryServices.put(type, dataRegistry.featureServices().register(
                        "ServerFeatures",
                        ownerFeature,
                        type,
                        instance
                ))
        );
    }

    /**
     * Unregisters one API owned by this feature.
     */
    public synchronized void unregisterService(Class<?> type) {
        Objects.requireNonNull(type, "type");

        Object instance = registeredServices.remove(type);
        if (instance != null) {
            APIRegistry.unregister(type, instance);
        }
        closeDataRegistryHandle(type);
    }

    /**
     * Unregisters all APIs owned by this feature.
     */
    public synchronized void unregisterAllServices() {
        for (var entry : new LinkedHashMap<>(registeredServices).entrySet()) {
            APIRegistry.unregister(entry.getKey(), entry.getValue());
            closeDataRegistryHandle(entry.getKey());
        }
        registeredServices.clear();
        dataRegistryServices.values().forEach(FeatureServiceHandle::close);
        dataRegistryServices.clear();
    }

    /**
     * Returns the number of APIs currently owned by this feature.
     */
    public synchronized int getRegisteredServiceCount() {
        return registeredServices.size();
    }

    private void closeDataRegistryHandle(Class<?> type) {
        FeatureServiceHandle handle = dataRegistryServices.remove(type);
        if (handle != null) {
            handle.close();
        }
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }
}
